package model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResultInfo {

    private boolean purchaseFound;
    private BigDecimal purchaseSum;
    private int nextItemPos;
}
