
package au.org.massive.strudel_web.jersey;

import au.org.massive.strudel_web.*;
import au.org.massive.strudel_web.Session;
import au.org.massive.strudel_web.job_control.*;
import au.org.massive.strudel_web.job_control.TaskFactory.Task;
import au.org.massive.strudel_web.ssh.SSHExecException;
import au.org.massive.strudel_web.tunnel.GuacamoleSession;
import au.org.massive.strudel_web.tunnel.HTTPTunnel;
import au.org.massive.strudel_web.tunnel.TunnelDependency;
import au.org.massive.strudel_web.tunnel.TunnelManager;
import com.google.gson.Gson;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.*;

/**
 * Endpoints that act on an HPC system
 *
 * @author jrigby
 */
@Path("/")
public class JobControlEndpoints extends Endpoint {

    private static final Settings settings = Settings.getInstance();

    private static final Logger logger = LogManager.getLogger(JobControlEndpoints.class);

    /**
     * Gets (and creates, if necessary) a session and returns the id and whether the session
     * currently has a certificate associated with it.
     *
     * @param request the {@link HttpServletRequest} object injected from the {@link Context}
     * @return a json object with some session info
     */
    @GET
    @Path("session_info")
    @Produces(MediaType.APPLICATION_JSON)
    public String getSessionInfo(@Context HttpServletRequest request) {
        Gson gson = new Gson();
        Map<String, String> response = new HashMap<>();
        String sessionId = request.getSession(true).getId();
        Session session;
        try {
            session = new Session(sessionId);
        } catch (NoSuchSessionException e1) {
            // If the server restarts and a client has a stale session, a new one is created
            request.getSession().invalidate();
            return getSessionInfo(request);
        }
        response.put("session_id", sessionId);
        response.put("has_certificate", String.valueOf(session.hasCertificate()));
        if (session.hasCertificate()) {
            response.put("uid", session.getCertificate().getUserName());
        } else {
            response.put("uid", "");
        }
        response.put("has_oauth_access_token", String.valueOf(session.hasOAuthAccessToken()));
        if (session.hasOAuthAccessToken()) {
            response.put("auth_backend_name", session.getSSHCertSigningBackend().getName());
        } else {
            response.put("auth_backend_name", "");
        }
        if (session.hasUserEmail()) {
            response.put("email", session.getUserEmail());
        } else {
            response.put("email", "");
        }
        return gson.toJson(response);
    }

    /**
     * Gets a key pair and get the public key signed
     *
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a status message
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("register_key")
    @Produces(MediaType.APPLICATION_JSON)
    public String registerKey(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSession(request);

        if (!session.hasOAuthAccessToken()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No access token for the SSH AuthZ server in session");
            return null;
        } else {
            try {
                session.setCertificate(KeyService.registerKey(session.getOAuthAccessToken(), session.getSSHCertSigningBackend()));
                Logging.accessLogger.info("User session started for " + getUserString(session));
            } catch (OAuthSystemException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return null;
            } catch (OAuthProblemException | UnauthorizedException e) {
                session.clearOAuthAccessToken();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "The user did not authorise this request during the OAuth2 flow, or authorization has expired.");
                return null;
            }
        }

        Gson gson = new Gson();
        Map<String, String> responseMessage = new HashMap<>();
        responseMessage.put("status", "OK");
        responseMessage.put("message", "Key pair generated and public key signed successfully");
        return gson.toJson(responseMessage);
    }

    private String getUserString(Session session) {
        return session.getCertificate().getUserName();
    }

    /**
     * Triggers a session logout
     *
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a status message
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("end_session")
    @Produces(MediaType.APPLICATION_JSON)
    public String invalidateSession(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Gson gson = new Gson();

        Session session = getSession(request);
        SessionManager.endSession(session);

        Map<String, String> responseMessage = new HashMap<>();
        responseMessage.put("message", "Session " + session.getSessionId() + " invalidated");

        return gson.toJson(responseMessage);
    }

    /**
     * Runs preconfigured commands on the remote HPC system. These commands are defined as part of a {@link TaskConfiguration} object.
     *
     * @param task     the name of the task to run
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return the result of the command
     * @throws IOException      thrown on network IO errors
     * @throws SSHExecException thrown if there are any issues executing the task via SSH
     */
    @GET
    @Path("/execute/{task}/")
    @Produces(MediaType.APPLICATION_JSON)
    public String executeJob0(@PathParam("task") String task, @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException, SSHExecException {
        return executeJob(null, task, null, request, response, 0);
    }

    /**
     * Runs preconfigured commands on the remote HPC system. These commands are defined as part of a {@link TaskConfiguration} object.
     *
     * @param host     the name of the host on which to run the task
     * @param task     the name of the task to run
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return the result of the command
     * @throws IOException      thrown on network IO errors
     * @throws SSHExecException thrown if there are any issues executing the task via SSH
     */
    @GET
    @Path("/execute/{task}/on/{host}/")
    @Produces(MediaType.APPLICATION_JSON)
    public String executeJob1(@PathParam("host") String host, @PathParam("task") String task, @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException, SSHExecException {
        return executeJob(host, task, null, request, response, 0);
    }

    /**
     * Runs preconfigured commands on the remote HPC system. These commands are defined as part of a {@link TaskConfiguration} object.
     *
     * @param configuration the name of the configuration from which the task should be run
     * @param task          the name of the task to run
     * @param request       the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response      the {@link HttpServletResponse} object injected from the {@link Context}
     * @return the result of the command
     * @throws IOException      thrown on network IO errors
     * @throws SSHExecException thrown if there are any issues executing the task via SSH
     */
    @GET
    @Path("/execute/{task}/in/{configuration}/")
    @Produces(MediaType.APPLICATION_JSON)
    public String executeJob2(@PathParam("task") String task, @PathParam("configuration") String configuration, @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException, SSHExecException {
        return executeJob(null, task, configuration, request, response, 0);
    }

    /**
     * Runs preconfigured commands on the remote HPC system. These commands are defined as part of a {@link TaskConfiguration} object.
     *
     * @param host          the name of the host on which to run the task
     * @param configuration the name of the configuration from which the task should be run
     * @param task          the name of the task to run
     * @param request       the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response      the {@link HttpServletResponse} object injected from the {@link Context}
     * @return the result of the command
     * @throws IOException      thrown on network IO errors
     * @throws SSHExecException thrown if there are any issues executing the task via SSH
     */
    @GET
    @Path("/execute/{task}/in/{configuration}/on/{host}/")
    @Produces(MediaType.APPLICATION_JSON)
    public String executeJob(@PathParam("host") String host,
                             @PathParam("task") String task,
                             @PathParam("configuration") String configuration,
                             @Context HttpServletRequest request,
                             @Context HttpServletResponse response,
                             @DefaultValue("0") @QueryParam("retries") Integer retries) throws IOException, SSHExecException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        ConfigurationRegistry systemConfigurations = settings.getSystemConfigurations();
        AbstractSystemConfiguration systemConfiguration = (configuration == null) ? systemConfigurations.getDefaultSystemConfiguration() : systemConfigurations.getSystemConfigurationById(configuration);
        if (systemConfiguration == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid configuration name");
            return null;
        }

        Task remoteTask;
        try {
            if (host == null) {
                if (systemConfiguration.findByTaskType(task).getRemoteHost().isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "This task requires a target host to be explicitly specified, e.g. /execute/" + task + "/in/" + configuration + "/on/127.0.0.1/");
                    return null;
                }
                remoteTask = new TaskFactory(systemConfiguration).getInstance(task, session);
            } else {
                remoteTask = new TaskFactory(systemConfiguration).getInstance(task, session, host);
            }

            Map<String, String> parameters = new HashMap<>();
            for (String key : request.getParameterMap().keySet()) {
                String value = request.getParameterMap().get(key)[0]; // Only one value is accepted
                parameters.put(key, value);
            }

            try {
                TaskResult<List<Map<String, String>>> result = remoteTask.run(parameters);
                Logging.accessLogger.info("Ran task \"" + task + "\" on \"" + host + "\" from configuration \"" + configuration + "\" for " + getUserString(session));
                if (!result.getUserMessages().isEmpty()) {
                    session.addUserMessages(result.getUserMessages(), configuration);
                }
                return result.getCommandResultAsJson();
            } catch (MissingRequiredTaskParametersException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return null;
            } catch (SSHExecException e1) {
                // If this request fails, try using the default remote host
                if (retries < 1 && !systemConfiguration.findByTaskType(task).getRemoteHost().isEmpty()) {
                    return executeJob(null, task, configuration, request, response, 1);
                } else {
                    throw e1;
                }
            }
        } catch (NoSuchTaskTypeException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

    }


    /**
     * Starts a VNC tunnel for use with a Guacamole server
     *
     * @param desktopName       the name to assign to the desktop
     * @param vncPassword       the password of the vnc server
     * @param remoteHost        the host on which the vnc server is running
     * @param display           the display number assigned to the vnc server
     * @param viaGateway        a gateway through which the tunnel is created (optional, can be inferred if configurationName provided)
     * @param configurationName the name of the configuration used for this tunnel (optional, recommended)
     * @param request           the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response          the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a vnc session id and desktop name
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("/startvnctunnel")
    @Produces(MediaType.APPLICATION_JSON)
    public String startVncTunnel(
            @QueryParam("desktopname") String desktopName,
            @QueryParam("vncpassword") String vncPassword,
            @QueryParam("remotehost") String remoteHost,
            @QueryParam("display") int display,
            @QueryParam("via_gateway") String viaGateway,
            @QueryParam("configuration") String configurationName,
            @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }
        int remotePort = display + 5900;

        // This code uses the configuration, if provided, to determine whether the tunnel should use
        // the login host as a gateway, or whether the tunnel is direct to the target.
        AbstractSystemConfiguration systemConfiguration = settings.getSystemConfigurations().getSystemConfigurationById(configurationName);
        if (viaGateway == null && (systemConfiguration == null || !systemConfiguration.isTunnelTerminatedOnLoginHost())) {
            viaGateway = remoteHost;
            remoteHost = "localhost";
        } else if (viaGateway == null) {
            viaGateway = systemConfiguration.getLoginHost();
        }

        GuacamoleSession guacSession = (GuacamoleSession) TunnelManager.startGuacamoleSession(desktopName, vncPassword, viaGateway, remoteHost, remotePort, session);

        Gson gson = new Gson();
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", guacSession.getId());
        responseData.put("desktopName", desktopName);
        responseData.put("localPort", guacSession.getLocalPort());
        return gson.toJson(responseData);
    }

    /**
     * Stops a guacamole VNC session
     *
     * @param guacSessionId id of the vnc tunnel session
     * @param request       the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response      the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a status message
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("/stopvnctunnel")
    @Produces(MediaType.APPLICATION_JSON)
    public String stopVncTunnel(
            @QueryParam("id") int guacSessionId,
            @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        GuacamoleSession guacSession = null;
        for (TunnelDependency s : session.getTunnelSessionsSet()) {
            if (s instanceof GuacamoleSession) {
                if (s.getId() == guacSessionId) {
                    guacSession = (GuacamoleSession) s;
                    break;
                }
            }
        }

        return stopTunnelMessage(response, guacSession);
    }

    /**
     * Lists all active VNC sessions for the current user
     *
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a list of tunnels
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("/listvnctunnels")
    @Produces(MediaType.APPLICATION_JSON)
    public String listVncTunnels(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        Set<TunnelDependency> tunnelSet = session.getTunnelSessionsSet();
        List<Map<String, Object>> tunnels = new ArrayList<>(tunnelSet.size());
        for (TunnelDependency s : tunnelSet) {
            if (s instanceof GuacamoleSession) {
                GuacamoleSession guacSession = (GuacamoleSession) s;
                Map<String, Object> tunnel = new HashMap<>();
                tunnels.add(tunnel);

                tunnel.put("id", guacSession.getId());
                tunnel.put("desktopName", guacSession.getName());
                tunnel.put("password", guacSession.getPassword());
                tunnel.put("localPort", guacSession.getLocalPort());
            }
        }

        Gson gson = new Gson();
        return gson.toJson(tunnels);
    }

    public void httpProxy(HttpServletRequest request, HttpServletResponse response, Integer proxyId, String remotePath, String method) throws IOException, InterruptedException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return;
        }
        for (TunnelDependency t : session.getTunnelSessionsSet()) {
            if (t instanceof HTTPTunnel && t.getId() == proxyId) {
                ((HTTPTunnel) t).doRequest(request, response, remotePath, method, session);
                return;
            }
        }
        response.setStatus(404);
    }

    @GET
    @Path("/proxy/{proxyId}/{remotePath : .*}")
    public void httpProxyGet(@Context HttpServletRequest request, @Context HttpServletResponse response, @PathParam("proxyId") Integer proxyId, @PathParam("remotePath") String remotePath) throws IOException, InterruptedException {
        httpProxy(request, response, proxyId, remotePath, "GET");
    }

    @POST
    @Path("/proxy/{proxyId}/{remotePath : .*}")
    public void httpProxyPost(@Context HttpServletRequest request, @Context HttpServletResponse response, @PathParam("proxyId") Integer proxyId, @PathParam("remotePath") String remotePath) throws IOException, InterruptedException {
        httpProxy(request, response, proxyId, remotePath, "POST");
    }

    @GET
    @Path("/starthttpproxy")
    @Produces(MediaType.APPLICATION_JSON)
    public String startHttpProxy(
            @QueryParam("remotehost") String remoteHost,
            @DefaultValue("/") @QueryParam("root") String root,
            @DefaultValue("80") @QueryParam("remoteport") Integer remotePort,
            @QueryParam("via_gateway") String viaGateway,
            @DefaultValue("false") @QueryParam("is_secure") Boolean isSecure,
            @QueryParam("configuration") String configurationName,
            @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        // This code uses the configuration, if provided, to determine whether the tunnel should use
        // the login host as a gateway, or whether the tunnel is direct to the target.
        AbstractSystemConfiguration systemConfiguration = settings.getSystemConfigurations().getSystemConfigurationById(configurationName);
        if (viaGateway == null && (systemConfiguration == null || !systemConfiguration.isTunnelTerminatedOnLoginHost())) {
            viaGateway = remoteHost;
            remoteHost = "localhost";
        } else if (viaGateway == null) {
            viaGateway = systemConfiguration.getLoginHost();
        }

        TunnelDependency httpTunnel = TunnelManager.startHttpTunnel(viaGateway, remoteHost, root, isSecure, remotePort, session);

        Gson gson = new Gson();
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", httpTunnel.getId());
        return gson.toJson(responseData);
    }

    @GET
    @Path("/stophttpproxy")
    @Produces(MediaType.APPLICATION_JSON)
    public String stopHttpProxy(
            @QueryParam("id") int proxyId,
            @Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        HTTPTunnel httpTunnel = null;
        for (TunnelDependency s : session.getTunnelSessionsSet()) {
            if (s instanceof HTTPTunnel) {
                if (s.getId() == proxyId) {
                    httpTunnel = (HTTPTunnel) s;
                    break;
                }
            }
        }

        return stopTunnelMessage(response, httpTunnel);
    }

    /**
     * Lists all active VNC sessions for the current user
     *
     * @param request  the {@link HttpServletRequest} object injected from the {@link Context}
     * @param response the {@link HttpServletResponse} object injected from the {@link Context}
     * @return a list of tunnels
     * @throws IOException thrown on network IO errors
     */
    @GET
    @Path("/listhttpproxies")
    @Produces(MediaType.APPLICATION_JSON)
    public String listHttpProxies(@Context HttpServletRequest request, @Context HttpServletResponse response) throws IOException {
        Session session = getSessionWithCertificateOrSendError(request, response);
        if (session == null) {
            return null;
        }

        Set<TunnelDependency> tunnelSet = session.getTunnelSessionsSet();
        List<Map<String, Object>> proxies = new ArrayList<>(tunnelSet.size());
        for (TunnelDependency s : tunnelSet) {
            if (s instanceof HTTPTunnel) {
                HTTPTunnel proxy = (HTTPTunnel) s;
                Map<String, Object> tunnel = new HashMap<>();
                proxies.add(tunnel);

                tunnel.put("id", proxy.getId());
                tunnel.put("remoteHost", proxy.getRemoteHost());
                tunnel.put("remotePort", proxy.getRemotePort());
            }
        }

        Gson gson = new Gson();
        return gson.toJson(proxies);
    }

    private static String stopTunnelMessage(@Context HttpServletResponse response, TunnelDependency tunnel) throws IOException {
        Gson gson = new Gson();
        Map<String, String> responseData = new HashMap<>();
        if (tunnel == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No active session found by supplied ID");
            return null;
        } else {
            TunnelManager.stopSession(tunnel);
            responseData.put("message", "session deleted");
        }
        return gson.toJson(responseData);
    }

    @GET
    @Path("/configurations/")
    @Produces("application/json")
    public String listConfigurations() {
        return Settings.getInstance().getSystemConfigurations().getSystemConfigurationAsJson();
    }

    /**
     * Returns a list of system messages
     *
     * @param request
     * @param response
     * @param tag
     * @param type
     * @return
     * @throws IOException
     */
    @GET
    @Path("/messages/")
    @Produces("application/json")
    public String systemMessages(@Context HttpServletRequest request, @Context HttpServletResponse response, @DefaultValue("") @QueryParam("tag") String tag, @QueryParam("type") String type) throws IOException {
        Gson gson = new Gson();
        if (tag == null || tag.isEmpty()) {
            return gson.toJson(getSession(request).getUserMessages(type));
        } else {
            return gson.toJson(getSession(request).getUserMessages(type, tag));
        }
    }

    @POST
    @Path("/feedback/")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void sendFeedback(@FormParam("feedback") String payload, @Context HttpServletRequest request, @Context HttpServletResponse response) throws MessagingException, IOException {

        Session strudelSession = getSession(request);

        if (settings.isFeedbackEmailEnabled()) {
            FeedbackDebugData debugData = FeedbackDebugData.fromJson(payload);
            javax.mail.Session session = javax.mail.Session.getDefaultInstance(settings.getSMTPProperties());
            session.setDebug(true);
            MimeMessage message = new MimeMessage(session);
            message.addRecipient(Message.RecipientType.TO, settings.getFeedbackToAddress());

            // Sometimes the email is not available, and hasUserEmail returns the user ID instead. Assume a valid
            // email address if the '@' symbol is present.
            if (strudelSession.hasUserEmail() && strudelSession.getUserEmail().contains("@")) {
                message.setFrom(new InternetAddress(strudelSession.getUserEmail()));
            } else {
                message.setFrom(settings.getFeedbackFromAddress());
            }

            // Subject line interpolation
            Map<String,String> subjectLineValues = new HashMap<String,String>();
            subjectLineValues.put("username", strudelSession.hasCertificate()?strudelSession.getCertificate().getUserName():"unknown user");
            subjectLineValues.put("email", strudelSession.hasUserEmail()?strudelSession.getUserEmail():"unknown email");
            StrSubstitutor sub = new StrSubstitutor(subjectLineValues);
            message.setSubject(sub.replace(settings.getFeedbackEmailSubject()));

            Multipart multipart = new MimeMultipart();
            BodyPart messageBodyPart;

            // Email content
            messageBodyPart = new MimeBodyPart();
            StringBuilder sb = new StringBuilder();
            sb.append("User information:\n");
            if (strudelSession.hasUserEmail() && strudelSession.getUserEmail().contains("@")) {
                sb.append("* Email: " + strudelSession.getUserEmail() + "\n");
            } else {
                sb.append("* Email: unknown\n");
            }
            if (strudelSession.hasCertificate()) {
                sb.append("* User id: " + strudelSession.getCertificate().getUserName() + "\n");
            } else {
                sb.append("* User id: unknown\n");
            }
            if (strudelSession.getSSHCertSigningBackend() != null) {
                sb.append("* Service: " + strudelSession.getSSHCertSigningBackend().getName() + "\n");
            }

            sb.append("\nMessage: \n");
            sb.append(debugData.note + "\n\n");
            sb.append("--- Additional info ---\n");
            sb.append(debugData.getBrowserInfo());
            messageBodyPart.setText(sb.toString());
            multipart.addBodyPart(messageBodyPart);

            // Attachment #1 Screenshot
            DataSource imageAttachmentDataSource = new ByteArrayDataSource(debugData.getImage(), "image/png");
            messageBodyPart = new MimeBodyPart();
            messageBodyPart.setDataHandler(new DataHandler(imageAttachmentDataSource));
            messageBodyPart.setFileName("screenshot.png");
            multipart.addBodyPart(messageBodyPart);

            // Attachment #2 HTML
            messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(debugData.html);
            messageBodyPart.setFileName("page.html");
            multipart.addBodyPart(messageBodyPart);

            message.setContent(multipart);
            Transport.send(message);
        } else {
            // If no email is setup, feedback is logged as-is.
            logger.warn("Feedback emails should be configured!");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
    }
}
