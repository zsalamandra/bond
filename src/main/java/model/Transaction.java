package model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class Transaction {
    private String id;
    private String customerId;
    private String accountId;
    private TransactionType transactionType;
    private String objectId;
    private ObjectType objectType;
    private BigDecimal incomingAmount;
    private BigDecimal outgoingAmount;
    private String currency;
    private String productId;
    private String productType;
    private int incomingProductCount;
    private int outgoingProductCount;
    private OffsetDateTime createdAt;
    private List<String> productInstancesIds;
    private String incomingChannel;

}