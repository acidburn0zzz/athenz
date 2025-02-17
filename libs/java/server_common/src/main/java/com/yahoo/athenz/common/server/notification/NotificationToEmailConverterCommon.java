/*
 *  Copyright 2020 Verizon Media
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.yahoo.athenz.common.server.notification;

import com.yahoo.athenz.auth.Authority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class NotificationToEmailConverterCommon {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToEmailConverterCommon.class);

    private static final String AT = "@";
    private static final String USER_DOMAIN_DEFAULT = "user";
    private static final String PROP_USER_DOMAIN = "athenz.user_domain";
    private static final String PROP_NOTIFICATION_EMAIL_DOMAIN_TO = "athenz.notification_email_domain_to";
    private static final String PROP_NOTIFICATION_WORKFLOW_URL = "athenz.notification_workflow_url";
    private static final String PROP_NOTIFICATION_ATHENZ_UI_URL = "athenz.notification_athenz_ui_url";
    private static final String PROP_NOTIFICATION_SUPPORT_TEXT = "athenz.notification_support_text";
    private static final String PROP_NOTIFICATION_SUPPORT_URL = "athenz.notification_support_url";
    private static final String PROP_NOTIFICATION_USER_AUTHORITY = "athenz.notification_user_authority";
    private static final String EMAIL_TEMPLATE_CSS = "emails/base.css";

    private static final String HTML_STYLE_TAG_START = "<style>";
    private static final String HTML_STYLE_TAG_END = "</style>";
    private static final String HTML_TBODY_TAG_START = "<tbody>";
    private static final String HTML_TBODY_TAG_END = "</tbody>";

    // can be moved to constructor which can take Locale as input parameter and return appropriate resource bundle
    private static final ResourceBundle RB = ResourceBundle.getBundle("messages/ServerCommon");

    private final String userDomainPrefix;
    private final String emailDomainTo;
    private final String workflowUrl;
    private final String athenzUIUrl;
    private final String supportText;
    private final String supportUrl;
    private final String emailBaseCSS;
    private final Authority notificationUserAuthority;


    public NotificationToEmailConverterCommon(Authority notificationUserAuthority) {
        String userDomain = System.getProperty(PROP_USER_DOMAIN, USER_DOMAIN_DEFAULT);
        userDomainPrefix = userDomain + "\\.";
        emailDomainTo = System.getProperty(PROP_NOTIFICATION_EMAIL_DOMAIN_TO);
        workflowUrl = System.getProperty(PROP_NOTIFICATION_WORKFLOW_URL);
        athenzUIUrl = System.getProperty(PROP_NOTIFICATION_ATHENZ_UI_URL);
        supportText = System.getProperty(PROP_NOTIFICATION_SUPPORT_TEXT);
        supportUrl = System.getProperty(PROP_NOTIFICATION_SUPPORT_URL);

        emailBaseCSS = readContentFromFile(getClass().getClassLoader(), EMAIL_TEMPLATE_CSS);

        // If a notificationUserAuthority is configured load it.
        final String configuredNotificationAuthority = System.getProperty(PROP_NOTIFICATION_USER_AUTHORITY);
        if (configuredNotificationAuthority != null) {
            this.notificationUserAuthority = loadNotificationUserAuthority(configuredNotificationAuthority);
        } else {
            this.notificationUserAuthority = notificationUserAuthority;
        }
    }

    private Authority loadNotificationUserAuthority(String className) {
        LOGGER.debug("Loading Notification user authority {}...", className);

        Authority authority;
        try {
            authority = (Authority) Class.forName(className).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            LOGGER.error("Invalid Notification user Authority class: {} error: {}", className, e.getMessage());
            return null;
        }
        return authority;
    }

    public String readContentFromFile(ClassLoader classLoader, String fileName) {
        StringBuilder contents = new StringBuilder();
        URL resource = classLoader.getResource(fileName);
        if (resource != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.openStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contents.append(line);
                    contents.append(System.getProperty("line.separator"));
                }
            } catch (IOException ex) {
                LOGGER.error("Could not read file: {}. Error message: {}", fileName, ex.getMessage());
            }
        }
        return contents.toString();
    }

    public String generateBodyFromTemplate(Map<String, String> metaDetails,
                                            String bodyTemplate,
                                            String bodyTemplateDetails,
                                            String tableValuesKey,
                                            int tableEntryNumColumns) {
        // first get the template and replace placeholders
        StringBuilder body = new StringBuilder(256);
        body.append(MessageFormat.format(bodyTemplate, metaDetails.get(bodyTemplateDetails), athenzUIUrl, supportUrl, supportText));

        // then get table rows and replace placeholders
        StringBuilder bodyEntry = new StringBuilder(256);
        final String tableValues = metaDetails.get(tableValuesKey);
        final String tableEntryTemplate = getTableEntryTemplate(tableEntryNumColumns);
        processEntry(bodyEntry, tableValues, tableEntryTemplate, tableEntryNumColumns);

        // add table rows to the template
        String bodyString = body.toString().replace(HTML_TBODY_TAG_START + HTML_TBODY_TAG_END, HTML_TBODY_TAG_START + bodyEntry + HTML_TBODY_TAG_END);

        // add css style to the template
        return addCssStyleToBody(bodyString);
    }

    public String getTableEntryTemplate(int numOfColumns) {
        StringBuilder tableEntryTemplate = new StringBuilder(256);
        tableEntryTemplate.append("<tr>");
        for (int i = 0; i < numOfColumns; ++i) {
            tableEntryTemplate.append("<td class=\"cv\">{");
            tableEntryTemplate.append(i);
            tableEntryTemplate.append("}</td>");
        }
        tableEntryTemplate.append("</tr>");
        return tableEntryTemplate.toString();
    }

    public String addCssStyleToBody(String body) {
        return body.replace(HTML_STYLE_TAG_START + HTML_STYLE_TAG_END, HTML_STYLE_TAG_START + emailBaseCSS + HTML_STYLE_TAG_END);
    }

    void processEntry(StringBuilder body, final String entryNames, final String entryFormat, int entryLength) {
        // if we have no entry names then there is nothing to process
        if (entryNames == null) {
            return;
        }
        String[] entries = entryNames.split("\\|");
        for (String entry : entries) {
            String[] comps = entry.split(";");
            if (comps.length != entryLength) {
                continue;
            }
            body.append(MessageFormat.format(entryFormat, comps));
            body.append('\n');
        }
    }

    public String getSubject(String propertyName) {
        return RB.getString(propertyName);
    }

    public Set<String> getFullyQualifiedEmailAddresses(Set<String> recipients) {
        return recipients.stream()
                .map(userName -> {
                    if (notificationUserAuthority != null) {
                        String email = notificationUserAuthority.getUserEmail(userName);
                        if (email != null) {
                            return email;
                        }
                    }
                    return userName.replaceAll(userDomainPrefix, "") + AT + emailDomainTo;
                })
                .collect(Collectors.toSet());
    }

    public String getWorkflowUrl() {
        return workflowUrl;
    }

    public String getAthenzUIUrl() {
        return athenzUIUrl;
    }
}
