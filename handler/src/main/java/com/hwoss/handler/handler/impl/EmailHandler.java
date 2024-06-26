package com.hwoss.handler.handler.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import com.common.constant.CommonConstant;
import com.common.domain.RecallTaskInfo;
import com.common.domain.TaskInfo;
import com.common.dto.model.ContentModel;
import com.common.dto.model.EmailContentModel;
import com.common.dto.model.SmsContentModel;
import com.common.enums.ChannelType;
import com.common.enums.RateLimitStrategy;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.RateLimiter;
import com.hwoss.handler.flowcontrol.FlowControlParam;
import com.hwoss.handler.handler.BaseHandler;
import com.hwoss.handler.handler.Handler;
import com.hwoss.suport.utils.AccountUtils;
import com.hwoss.suport.utils.FileUtils;
import com.sun.mail.util.MailSSLSocketFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@Slf4j
public class EmailHandler extends BaseHandler implements Handler {

    @Autowired
    private AccountUtils accountUtils;

    @Value("${hwoss.business.upload.crowd.path}")
    private String dataPath;



    //    初始化对应的channelCode和对应的限流规则自定义
    public EmailHandler() {
        channelCode = ChannelType.EMAIL.getCode();
        // 按照请求限流，默认单机 3 qps （具体数值配置在apollo动态调整)
        Double rateInitValue = Double.valueOf(3);
        flowControlParam = FlowControlParam.builder()
                .rateInitValue(rateInitValue)
                .rateLimitStrategy(RateLimitStrategy.REQUEST_RATE_LIMIT)
                .rateLimiter(RateLimiter.create(rateInitValue))
                .build();
    }

    /**
     * 获取账号信息和配置
     *
     * @return
     */
    private MailAccount getAccountConfig(Integer sendAccount) {
        MailAccount account = accountUtils.getAccountById(sendAccount, MailAccount.class);
        try {
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            account.setAuth(account.isAuth()).setStarttlsEnable(account.isStarttlsEnable()).setSslEnable(account.isSslEnable()).setCustomProperty("mail.smtp.ssl.socketFactory", sf);
            account.setTimeout(25000).setConnectionTimeout(25000);
        } catch (Exception e) {
            log.error("EmailHandler#getAccount fail!{}", Throwables.getStackTraceAsString(e));
        }
        return account;
    }


    @Override
    public boolean handler(TaskInfo taskInfo) {
        EmailContentModel emailContentModel = (EmailContentModel) taskInfo.getContentModel();
        MailAccount account = getAccountConfig(taskInfo.getSendAccount());
        List<File> files = null;
        try {
            if (CharSequenceUtil.isNotBlank(emailContentModel.getUrl())) {
                files = FileUtils.getRemoteUrl2File(dataPath, CharSequenceUtil.split(emailContentModel.getUrl(), StrPool.COMMA));
            }

            if (CollUtil.isEmpty(files)) {
                MailUtil.send(account, taskInfo.getReceivers(), emailContentModel.getTitle(), emailContentModel.getContent(), true);
            } else {
                MailUtil.send(account, taskInfo.getReceivers(), emailContentModel.getTitle(), emailContentModel.getContent(), true, files.toArray(new File[0]));
            }
        } catch (Exception e) {
            log.error("EmailHandler#handler fail!{},params:{}", Throwables.getStackTraceAsString(e), taskInfo);

            return false;
        }
        return true;
    }

    /**
     * 邮箱 api 不支持撤回消息
     *
     * @param recallTaskInfo
     */
    @Override
    public void handleRecall(RecallTaskInfo recallTaskInfo) {

    }
}
