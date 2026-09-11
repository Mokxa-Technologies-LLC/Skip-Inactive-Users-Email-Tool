package org.joget.enhancedemailtool.app;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.lib.EmailTool;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormPdfUtil;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;

public class EmailAuditTool extends DefaultApplicationPlugin implements PluginWebSupport {

    private FormDataDao formDataDao;
    private UserDao userDao;

    public String getName() {
        return "Skip Inactive Users Email Tool";
    }

    public String getDescription() {
        return "Skip Inactive Users Email Tool";
    }

    public String getVersion() {
        return "1.0";
    }

    public String getLabel() {
        return "Skip Inactive Users Email Tool";
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/enhancedEmailTool.json",
            null,
            true,
            null
        );
    }

    @Override
    public Object execute(Map properties) {

        this.formDataDao =
            (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

        this.userDao =
            (UserDao) AppUtil.getApplicationContext().getBean("userDao");

        WorkflowAssignment wfAssignment =
            (WorkflowAssignment) properties.get("workflowAssignment");

        AppDefinition appDef =
            (AppDefinition) properties.get("appDef");

        String cc = (String) properties.get("cc");
        String bcc = (String) properties.get("bcc");
        String toParticipantId = (String) properties.get("toParticipantId");
        String toSpecific = (String) properties.get("toSpecific");

        try {

            List<String> toAddress = new ArrayList<String>();
            List<String> ccAddress = new ArrayList<String>();
            List<String> bccAddress = new ArrayList<String>();

            /*
             * =========================
             * PROCESS CC RECIPIENTS
             * =========================
             */
            if (cc != null && !cc.trim().isEmpty()) {

                Collection<String> ccs =
                    AppUtil.getEmailList(null, cc, wfAssignment, appDef);

                if (ccs != null) {
                    for (String address : ccs) {

                        if (isUserActiveByEmail(address)) {
                            ccAddress.add(StringUtil.encodeEmail(address));

                            LogUtil.info(
                                getClass().getName(),
                                "CC recipient accepted: " + address
                            );
                        } else {

                            LogUtil.info(
                                getClass().getName(),
                                "CC recipient skipped because user is inactive: " + address
                            );
                        }
                    }
                }
            }

            /*
             * =========================
             * PROCESS BCC RECIPIENTS
             * =========================
             */
            if (bcc != null && !bcc.trim().isEmpty()) {

                Collection<String> bccs =
                    AppUtil.getEmailList(null, bcc, wfAssignment, appDef);

                if (bccs != null) {
                    for (String address : bccs) {

                        if (isUserActiveByEmail(address)) {
                            bccAddress.add(StringUtil.encodeEmail(address));

                            LogUtil.info(
                                getClass().getName(),
                                "BCC recipient accepted: " + address
                            );
                        } else {

                            LogUtil.info(
                                getClass().getName(),
                                "BCC recipient skipped because user is inactive: " + address
                            );
                        }
                    }
                }
            }

            /*
             * =========================
             * PROCESS TO RECIPIENTS
             * =========================
             */
            if ((toParticipantId != null && !toParticipantId.trim().isEmpty())
                    || (toSpecific != null && !toSpecific.trim().isEmpty())) {

                Collection<String> tos =
                    AppUtil.getEmailList(
                        toParticipantId,
                        toSpecific,
                        wfAssignment,
                        appDef
                    );

                if (tos != null) {
                    for (String address : tos) {

                        if (isUserActiveByEmail(address)) {
                            toAddress.add(StringUtil.encodeEmail(address));

                            LogUtil.info(
                                getClass().getName(),
                                "TO recipient accepted: " + address
                            );
                        } else {

                            LogUtil.info(
                                getClass().getName(),
                                "TO recipient skipped because user is inactive: " + address
                            );
                        }
                    }
                }
            }

            /*
             * =========================
             * UPDATE EMAIL PROPERTIES
             * =========================
             */
            properties.put("toParticipantId", "");
            properties.put("toSpecific", String.join(",", toAddress));
            properties.put("cc", String.join(",", ccAddress));
            properties.put("bcc", String.join(",", bccAddress));

            /*
             * =========================
             * SEND EMAIL
             * =========================
             *
             * Send only if at least one active/external
             * recipient is available.
             */
            if (!toAddress.isEmpty()
                    || !ccAddress.isEmpty()
                    || !bccAddress.isEmpty()) {

                EmailTool tool = new EmailTool();

                try {

                    tool.execute(properties);

                    LogUtil.info(
                        getClass().getName(),
                        "Email sent successfully. "
                        + "TO=" + String.join(",", toAddress)
                        + ", CC=" + String.join(",", ccAddress)
                        + ", BCC=" + String.join(",", bccAddress)
                    );

                } catch (Exception ex) {

                    LogUtil.error(
                        getClass().getName(),
                        ex,
                        "Error while sending email."
                    );
                }

            } else {

                LogUtil.info(
                    getClass().getName(),
                    "No active recipients found. Email will not be sent."
                );
            }

        } catch (Exception e) {

            LogUtil.error(
                getClass().getName(),
                e,
                "Unexpected error occurred while processing email recipients."
            );
        }

        return null;
    }

    /**
     * Checks whether the email belongs to an active Joget user.
     *
     * Rules:
     *
     * 1. User exists in Joget and active = 1
     *    -> return true
     *
     * 2. User exists in Joget and active != 1
     *    -> return false
     *
     * 3. User does not exist in Joget directory
     *    -> return true (external email)
     *
     * 4. Empty email
     *    -> return false
     */
    private boolean isUserActiveByEmail(String email) {

        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String emailToCheck = email.trim();

        try {

            Collection<User> users = this.userDao.getUsers(
                emailToCheck,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

            /*
             * User found in Joget directory
             */
            if (users != null && !users.isEmpty()) {

                for (User user : users) {

                    LogUtil.info(
                        getClass().getName(),
                        "User lookup - "
                        + "Email=" + user.getEmail()
                        + ", Username=" + user.getUsername()
                        + ", Active=" + user.getActive()
                    );

                    /*
                     * Make sure the returned user is actually
                     * the email address being checked.
                     */
                    if (user.getEmail() != null
                            && emailToCheck.equalsIgnoreCase(
                                user.getEmail().trim())) {

                        /*
                         * Joget active = 1
                         */
                        if (Integer.valueOf(1).equals(user.getActive())) {

                            LogUtil.info(
                                getClass().getName(),
                                "ACTIVE user. Email allowed: "
                                + emailToCheck
                            );

                            return true;

                        } else {

                            LogUtil.info(
                                getClass().getName(),
                                "INACTIVE user. Email blocked: "
                                + emailToCheck
                            );

                            return false;
                        }
                    }
                }
            }

            /*
             * User is not present in Joget directory.
             * Treat as external email and allow it.
             */
            LogUtil.info(
                getClass().getName(),
                "No Joget directory user found for email. "
                + "Allowing external email: "
                + emailToCheck
            );

            return true;

        } catch (Exception e) {

            LogUtil.error(
                getClass().getName(),
                e,
                "Error checking user status for email: "
                + emailToCheck
            );

            /*
             * Allow email if directory lookup fails,
             * preserving the external-email behavior.
             */
            return true;
        }
    }

    protected Form getForm(String formDefId) {

        Form form = null;

        if (formDefId != null && !formDefId.isEmpty()) {

            AppDefinition appDef =
                AppUtil.getCurrentAppDefinition();

            if (appDef != null) {

                FormDefinitionDao formDefinitionDao =
                    (FormDefinitionDao) AppUtil.getApplicationContext()
                        .getBean("formDefinitionDao");

                FormService formService =
                    (FormService) AppUtil.getApplicationContext()
                        .getBean("formService");

                FormDefinition formDef =
                    formDefinitionDao.loadById(formDefId, appDef);

                if (formDef != null) {

                    String json = formDef.getJson();

                    form =
                        (Form) formService.createElementFromJson(json);

                    Boolean readonly =
                        Boolean.valueOf(
                            "true".equalsIgnoreCase(
                                getPropertyString("readonly")
                            )
                        );

                    Boolean readonlyLabel =
                        Boolean.valueOf(
                            "true".equalsIgnoreCase(
                                getPropertyString("readonlyLabel")
                            )
                        );

                    if (readonly.booleanValue()
                            || readonlyLabel.booleanValue()) {

                        FormUtil.setReadOnlyProperty(
                            form,
                            readonly,
                            readonlyLabel
                        );
                    }
                }
            }
        }

        return form;
    }

    @Override
    public void webService(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        AppDefinition appDef =
            AppUtil.getCurrentAppDefinition();

        byte[] data =
            FormPdfUtil.createPdf(
                request.getParameter("formDefId"),
                request.getParameter("id"),
                appDef,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );

        response.setContentType("application/pdf");

        response.addHeader(
            "Content-Disposition",
            "attachment; filename="
            + request.getParameter("id")
            + ".pdf"
        );

        response.setContentLength(data.length);

        OutputStream writer =
            response.getOutputStream();

        writer.write(data);
        writer.close();
    }
}