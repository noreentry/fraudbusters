package com.rbkmoney.fraudbusters.fraud.aggragator;

import com.rbkmoney.fraudbusters.repository.FraudResultRepository;
import com.rbkmoney.fraudbusters.util.TimestampUtil;
import com.rbkmoney.fraudo.aggregator.CountAggregator;
import com.rbkmoney.fraudo.constant.CheckedField;
import com.rbkmoney.fraudo.model.FraudModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class CountAggregatorImpl implements CountAggregator {

    private final FraudResultRepository fraudResultRepository;

    @Override
    public Integer count(CheckedField checkedField, FraudModel fraudModel, Long aLong) {
        Instant now = Instant.now();
        Integer count = fraudResultRepository.countOperationByEmail(fraudModel.getEmail(), TimestampUtil.generateTimestampMinusMinutes(now, aLong),
                TimestampUtil.generateTimestampNow(now));
        log.debug("CountAggregatorImpl count: {}", count);
        return count;
    }

    @Override
    public Integer countSuccess(CheckedField checkedField, FraudModel fraudModel, Long aLong) {
        Instant now = Instant.now();
        return fraudResultRepository.countOperationByEmailSuccess(fraudModel.getEmail(), TimestampUtil.generateTimestampNow(now),
                TimestampUtil.generateTimestampMinusMinutes(now, aLong));
    }

    @Override
    public Integer countError(CheckedField checkedField, FraudModel fraudModel, Long aLong, String s) {
        Instant now = Instant.now();
        return fraudResultRepository.countOperationByEmailError(fraudModel.getEmail(), TimestampUtil.generateTimestampNow(now),
                TimestampUtil.generateTimestampMinusMinutes(now, aLong));
    }
}
