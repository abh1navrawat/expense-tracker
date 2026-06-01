package com.tracker.expense.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendMonthlySummaryHtml(String toEmail, String subject, String htmlContent) {
        logger.info("Preparing monthly summary email for recipient: {}", toEmail);
        logger.debug("HTML Content preview: \n{}", htmlContent);

        if (mailSender == null) {
            logger.warn("JavaMailSender is not initialized or configured in application.properties. SMTP logs compiled: \nRecipient: {}\nSubject: {}\nContent: \n{}", toEmail, subject, htmlContent);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("no-reply@tracker.com");

            mailSender.send(message);
            logger.info("Monthly summary HTML email successfully sent to: {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send monthly summary email to {}", toEmail, e);
        }
    }
}
