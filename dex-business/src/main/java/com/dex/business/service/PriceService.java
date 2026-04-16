package com.dex.business.service;

import com.dex.data.entity.Price;
import com.dex.data.repository.PriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 价格计算服务
 */
@Service
@RequiredArgsConstructor
public class PriceService {
    private final PriceRepository priceRepository;

    public Optional<Price> getLatestPrice(String pair) {
        return Optional.ofNullable(priceRepository.findTopByPairOrderByTimestampDesc(pair));
    }

    public List<Price> getPriceHistory(String pair) {
        return priceRepository.findByPairOrderByTimestampDesc(pair);
    }

    public Price savePrice(Price price) {
        if (price.getCreatedAt() == null) {
            price.setCreatedAt(LocalDateTime.now());
        }
        priceRepository.save(price);
        return price;
    }
}
