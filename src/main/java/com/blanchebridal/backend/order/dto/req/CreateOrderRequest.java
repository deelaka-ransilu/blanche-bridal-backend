package com.blanchebridal.backend.order.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
    private String notes;
    private String fulfillmentMethod;
    private String deliveryAddress;
    private String customerPhone;
    private String orderMode;
}