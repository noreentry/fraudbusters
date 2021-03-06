package com.rbkmoney.fraudbusters.resource.handler;

import com.rbkmoney.damsel.fraudbusters.Template;
import com.rbkmoney.damsel.fraudbusters.ValidateTemplateResponse;
import com.rbkmoney.fraudbusters.fraud.p2p.validator. P2PTemplateValidator;
import com.rbkmoney.fraudbusters.fraud.validator.ListTemplateValidatorImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

@Slf4j
class P2PTemplateValidatorHandlerTest {

    P2PTemplateValidatorHandler p2pTemplateValidatorHandler = new P2PTemplateValidatorHandler(
            new ListTemplateValidatorImpl(new P2PTemplateValidator()));

    @Test
    void validateCompilationTemplateEmptyList() throws TException {
        ArrayList<Template> list = new ArrayList<>();
        ValidateTemplateResponse validateTemplateResponse = p2pTemplateValidatorHandler.validateCompilationTemplate(list);
        assertNotNull(validateTemplateResponse.getErrors());
        assertTrue(validateTemplateResponse.getErrors().isEmpty());
    }

    @Test
    void validateCompilationTemplateSuccessList() throws TException {
        ArrayList<Template> list = new ArrayList<>();
        list.add(createTemplate("test_1", "rule: inBlackList(\"email\")-> notify;"));
        list.add(createTemplate("test_2", "rule: inWhiteList(\"email\")-> notify;"));
        list.add(createTemplate("test_3", "rule:white:inWhiteList(\"email\",\"fingerprint\",\"card_token\",\"bin\",\"ip\")->accept;rule:black:inBlackList(\"email\",\"fingerprint\",\"card_token\",\"ip\")->decline;rule:highirsk_geo:in(countryBy(\"country_bank\"),\"IRN\",\"IRQ\",\"YEM\",\"PSE\",\"MMR\",\"SYR\")->decline;rule:cards_email_count_3:unique(\"email\",\"card_token\",1440)>2->decline;rule:cards_device_count_4:unique(\"fingerprint\",\"card_token\",1440)>3 AND not in(countryBy(\"country_bank\"),\"ARM\",\"AZE\")->decline;rule:count5:count(\"card_token\",1440,\"party_id\")>1 AND not in(countryBy(\"country_bank\"),\"UKR\",\"UZB\")->decline;"));
        ValidateTemplateResponse validateTemplateResponse = p2pTemplateValidatorHandler.validateCompilationTemplate(list);
        assertNotNull(validateTemplateResponse.getErrors());
        assertTrue(validateTemplateResponse.getErrors().isEmpty());
    }

    @Test
    void validateCompilationTemplateErrorList() throws TException {
        ArrayList<Template> list = new ArrayList<>();
        list.add(createTemplate("test_1", "rule: inBlackList(\"email\")-> notify;"));
        list.add(createTemplate("test_2", "rule:inWhiteList(\"email\")-> notify;"));
        String errorTemplId = "test_3";
        list.add(createTemplate(errorTemplId, "rule:dfs:countSuccess(\"email\", 1444) > 5->accept;"));
        ValidateTemplateResponse validateTemplateResponse = p2pTemplateValidatorHandler.validateCompilationTemplate(list);
        assertNotNull(validateTemplateResponse.getErrors());
        assertEquals(errorTemplId, validateTemplateResponse.getErrors().get(0).id);
    }

    private Template createTemplate(String id, String template) {
        return new Template()
                .setTemplate(template.getBytes())
                .setId(id);
    }

}