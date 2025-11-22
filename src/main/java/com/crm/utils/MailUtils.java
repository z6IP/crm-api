package com.crm.utils;

import com.crm.common.exception.ServerException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class MailUtils {

    private final JavaMailSender javaMailSender;

    private final String fromEmail = "1967430835@qq.com";

    // 发送合同审核通过通知邮件
    public void sendContractPassMail(String toEmail, String contractName, String contractNumber) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("【合同审核通过通知】");
            message.setText(String.format(
                    "尊敬的销售：\n\n您创建的合同已审核通过！\n合同名称：%s\n合同编号：%s\n\n请及时跟进后续业务流程。",
                    contractName, contractNumber
            ));
            javaMailSender.send(message);
            log.info("向邮箱{}发送合同审核通过邮件成功", toEmail);
        } catch (Exception e) {
            log.error("向邮箱{}发送合同审核通过邮件失败", toEmail, e);
            throw new ServerException("邮件发送失败，请联系管理员");
        }
    }
}