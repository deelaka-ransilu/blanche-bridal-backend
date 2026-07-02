package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.OrderMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
    private String notes;
    private String fulfillmentMethod;
    private String deliveryAddress;
    private String customerPhone;
    private OrderMode orderMode;

    // Staff-only: target customer when ADMIN/EMPLOYEE creates on behalf of a customer.
    // Ignored silently if the caller is a CUSTOMER.
    private UUID customerId;
}