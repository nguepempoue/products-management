package com.orderservice.orderservice.service;

import com.orderservice.orderservice.dto.InventoryResponse;
import com.orderservice.orderservice.dto.OrderLineItemsDto;
import com.orderservice.orderservice.dto.OrderRequest;
import com.orderservice.orderservice.event.OrderPlacedEvent;
import com.orderservice.orderservice.model.Order;
import com.orderservice.orderservice.model.OrderLineItems;
import com.orderservice.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    private final WebClient.Builder webClientBuilder;

    private final KafkaTemplate<String, OrderPlacedEvent> kafkatemplate;

    public String placeOlder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        List <String> skuCodes = order.getOrderLineItemsList()
                .stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        //Call Inventory Service, and place order if product is in stock
        InventoryResponse[] InventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductinStock = Arrays.stream(InventoryResponseArray).allMatch(InventoryResponse::isInStock);

        log.info( "result:: " + allProductinStock);

        if(allProductinStock){
            kafkatemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
            orderRepository.save(order);
            return "Order saved successfully !";

        }else{
            throw new IllegalStateException("Product is not in stock, please try agan later");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto){
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
