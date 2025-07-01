package tracego.tracegoserver.service;

import tracego.tracegoserver.dto.ResultMessage;
import tracego.tracegoserver.entity.CartList;
import tracego.tracegoserver.entity.Item;
import tracego.tracegoserver.entity.User;

public interface CartListService {

    // 장바구니에 상품 추가
    ResultMessage addItem(User user, Item item, int quantity, Long totalPrice);

    // 장바구니에서 상품 삭제
    ResultMessage deleteItem(User user, String uniqueValue);

    // 장바구니에서 아이디로 상품 삭제
    ResultMessage deleteItemById(User user, Long id);

    // 장바구니 목록 가져오기
    CartList getCartItems(User user);

    // 결제
    ResultMessage payment(User user);
}
