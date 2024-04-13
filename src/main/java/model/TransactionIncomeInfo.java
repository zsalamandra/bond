package model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class TransactionIncomeInfo {

    private String id;
    private ObjectType objectType;
    private int productCount;
    private BigDecimal amount;
    private OffsetDateTime createdAt;
}
