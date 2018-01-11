package au.org.massive.strudel_web;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import au.org.massive.strudel_web.job_control.AsyncTaskHistory;
import au.org.massive.strudel_web.job_control.TaskResult;
import au.org.massive.strudel_web.job_control.UserMessage;
import au.org.massive.strudel_web.ssh.CertAuthInfo;
import au.org.massive.strudel_web.tunnel.TunnelDependency;
import au.org.massive.strudel_web.util.FixedSizeStack;
import au.org.massive.strudel_web.tunnel.GuacamoleSession;

/**
 * An abstraction from the {@link HttpSession} object, mediating access to session attributes
 *
 * @author jrigby
 */
public class Session {

    private final HttpSession session;

    public Session(String sessionId) throws NoSuchSessionException {
        session = SessionManager.getSessionById(sessionId);
        if (session == null) {
            throw new NoSuchSessionException();
        }
    }

    public Session(HttpSession session) {
        this.session = session;
    }

    public Session(HttpServletRequest request) {
        this(request.getSession());
    }

    public HttpSession getHttpSession() {
        return session;
    }

    public String getSessionId() {
        return session.getId();
    }

    // Session attribute keys
    private static final String USER_EMAIL = "user-email";
    private static final String KEY_CERT = "ssh-certificate";
    private static final String OAUTH_BACKEND = "oauth-backend";
    private static final String OAUTH_ACCESS_TOKEN = "oauth-access-token";
    private static final String TUNNEL_SESSION = "tunnel-session";
    private static final String USER_MESSAGE_QUEUE = "user-message-queue";
    private static final String ASYNC_TASK_HISTORY = "task-history";

    public void setUserEmail(String email) {
        session.setAttribute(USER_EMAIL, email);
    }

    public String getUserEmail() {
        return (String) session.getAttribute(USER_EMAIL);
    }

    public boolean hasUserEmail() {
        return getUserEmail() != null;
    }

    public void setCertificate(CertAuthInfo cert) {
        session.setAttribute(KEY_CERT, cert);
    }

    public CertAuthInfo getCertificate() {
        return (CertAuthInfo) session.getAttribute(KEY_CERT);
    }

    public boolean hasCertificate() {
        return getCertificate() != null;
    }

    public void setSSHCertSigningBackend(SSHCertSigningBackend authBackend) {
        session.setAttribute(OAUTH_BACKEND, authBackend);
    }

    public SSHCertSigningBackend getSSHCertSigningBackend() {
        return (SSHCertSigningBackend) session.getAttribute(OAUTH_BACKEND);
    }

    public void setOAuthAccessToken(String accessToken) {
        session.setAttribute(OAUTH_ACCESS_TOKEN, accessToken);
    }

    public String getOAuthAccessToken() {
        return (String) session.getAttribute(OAUTH_ACCESS_TOKEN);
    }

    public boolean hasOAuthAccessToken() {
        return session.getAttribute(OAUTH_ACCESS_TOKEN) != null;
    }

    public void clearOAuthAccessToken() {
        session.removeAttribute(OAUTH_ACCESS_TOKEN);
    }

    @SuppressWarnings("unchecked")
    public synchronized Set<TunnelDependency> getTunnelSessionsSet() {
        Set<TunnelDependency> allTunnelSessions = (Set<TunnelDependency>) session.getAttribute(TUNNEL_SESSION);
        if (session.getAttribute(TUNNEL_SESSION) == null) {
            allTunnelSessions = Collections.newSetFromMap(new ConcurrentHashMap<TunnelDependency, Boolean>());
            session.setAttribute(TUNNEL_SESSION, allTunnelSessions);
        }

        // Handle multiple user sessions - return all Guacamole sessions belonging to the user even if over multiple sessions
        for (Session activeSession : SessionManager.getActiveSessions()) {
            if (!activeSession.getSessionId().equals(this.getSessionId()) && activeSession.hasCertificate() && this.hasCertificate() &&
                    activeSession.getCertificate().getUserName().equals(this.getCertificate().getUserName())) {
                Set<TunnelDependency> otherTunnelSessions = (Set<TunnelDependency>) activeSession.getHttpSession().getAttribute(TUNNEL_SESSION);
                if (otherTunnelSessions != null) {
                    allTunnelSessions.addAll(otherTunnelSessions);
                    otherTunnelSessions.clear();
                }
            }
        }

        return allTunnelSessions;
    }

    private Map<String, FixedSizeStack<UserMessage>> getUserMessageStacks() {
        Map<String, FixedSizeStack<UserMessage>> messageStacks = (Map<String, FixedSizeStack<UserMessage>>) session.getAttribute(USER_MESSAGE_QUEUE);
        if (messageStacks == null) {
            messageStacks = new HashMap<>();
            session.setAttribute(USER_MESSAGE_QUEUE, messageStacks);
        }
        return messageStacks;
    }

    public Set<UserMessage> getUserMessages(String type, String tag) {
        return getUserMessages(UserMessage.MessageType.fromString(type), tag);
    }

    public Map<String,Set<UserMessage>> getUserMessages(String type) {
        return getUserMessages(UserMessage.MessageType.fromString(type));
    }

    public Map<String,Set<UserMessage>> getUserMessages(UserMessage.MessageType type) {
        TreeMap<String,Set<UserMessage>> messages = new TreeMap<String,Set<UserMessage>>();
        for (String tag : getUserMessageStacks().keySet()) {
            messages.put(tag, getUserMessages(type, tag));
        }
        return messages;
    }

    public AsyncTaskHistory<TaskResult<List<Map<String, String>>>> getAsyncTaskHistory() {
        AsyncTaskHistory<TaskResult<List<Map<String, String>>>> tasks = (AsyncTaskHistory<TaskResult<List<Map<String, String>>>>) session.getAttribute(ASYNC_TASK_HISTORY);
        if (tasks == null) {
            tasks = new AsyncTaskHistory<>(100);
            session.setAttribute(USER_MESSAGE_QUEUE, tasks);
        }
        return tasks;
    }

    /**
     * Gets a set of messages of the given type to display to the user.
     *
     * @param type          type of message, null for all
     * @return a set of messages
     */
    public Set<UserMessage> getUserMessages(UserMessage.MessageType type, String tag) {

        // Filter tags
        Stack<UserMessage> messageStack = getUserMessageStacks().get(tag);

        TreeSet<UserMessage> messages = new TreeSet<>(new Comparator<UserMessage>() {
            @Override
            public int compare(UserMessage o1, UserMessage o2) {
                if (o1.getTimestamp() == o2.getTimestamp()) {
                    return 0;
                } else {
                    return (o1.getTimestamp() < o2.getTimestamp()) ? -1 : 1;
                }
            }
        });

        if (type == null) {
            messages.addAll(messageStack);
        } else {
            for (UserMessage msg : messageStack) {
                if (msg.getType() == type) {
                    messages.add(msg);
                }
            }
        }
        return messages;
    }

    public void addUserMessage(UserMessage message, String tag) {
        Map<String, FixedSizeStack<UserMessage>> messageStacks = getUserMessageStacks();
        FixedSizeStack<UserMessage> messageStack = messageStacks.get(tag);
        if (messageStack == null) {
            messageStack = new FixedSizeStack<>(50);
            messageStacks.put(tag, messageStack);
        }

        // If the message is being repeated, increment the count and timestamp
        for (UserMessage msg : messageStack) {
            if (msg.getMessage().equals(message.getMessage())) {
                msg.incrementCount();
                return;
            }
        }
        messageStack.push(message);
    }

    public void addUserMessages(Collection<UserMessage> messages, String tag) {
        for (UserMessage msg : messages) {
            addUserMessage(msg, tag);
        }
    }

    @Override
    public boolean equals(Object o) {
        return getSessionId().equals(o);
    }

    @Override
    public int hashCode() {
        return getSessionId().hashCode();
    }
}
