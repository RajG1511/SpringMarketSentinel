package com.raj.springmarketanalysis.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends alert emails via SMTP. Spring Boot only auto-configures a {@link JavaMailSender}
 * when {@code spring.mail.host} is set, so it is injected through an {@link ObjectProvider}
 * and the channel simply reports itself disabled when mail (or a recipient) is not
 * configured. See EMAIL_SETUP.md for the credentials to supply.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailHost;
    private final String to;
    private final String from;

    public EmailNotificationChannel(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${market.alerts.email.to:}") String to,
            @Value("${market.alerts.email.from:alerts@market-sentinel.local}") String from
    ) {
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.to = to;
        this.from = from;
    }

    @Override
    public String type() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return mailHost != null && !mailHost.isBlank()
                && to != null && !to.isBlank()
                && mailSender.getIfAvailable() != null;
    }

    @Override
    public void send(AlertNotification n) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("JavaMailSender is not configured (set spring.mail.host).");
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("[" + n.severity() + "] " + n.symbol() + " " + n.ruleKey());
        msg.setText(n.symbol() + " on " + n.ts() + "\n\n" + n.message());

        sender.send(msg);
    }
}
