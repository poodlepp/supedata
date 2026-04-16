package com.dex.data.repository;

import com.dex.data.entity.Price;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PriceRepository {
    @Select("""
            SELECT id, pair, price, timestamp, created_at
            FROM prices
            WHERE pair = #{pair}
            ORDER BY timestamp DESC
            LIMIT 1
            """)
    Price findTopByPairOrderByTimestampDesc(String pair);

    @Select("""
            SELECT id, pair, price, timestamp, created_at
            FROM prices
            WHERE pair = #{pair}
            ORDER BY timestamp DESC
            """)
    List<Price> findByPairOrderByTimestampDesc(String pair);

    @Insert("""
            INSERT INTO prices (pair, price, timestamp, created_at)
            VALUES (#{pair}, #{price}, #{timestamp}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int save(Price price);
}
