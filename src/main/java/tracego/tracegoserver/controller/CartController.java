package tracego.tracegoserver.controller;


import tracego.tracegoserver.dto.BodyItem;
import tracego.tracegoserver.dto.DeleteCartItemRequest;
import tracego.tracegoserver.dto.ResultMessage;
import tracego.tracegoserver.entity.*;
import tracego.tracegoserver.service.CartListService;
import tracego.tracegoserver.service.ItemService;
import tracego.tracegoserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Controller
public class CartController {

    // 의존성을 주입하기 위한 부분 ⬇ ==================================================

    CartListService cartListService;
    ItemService itemService;
    UserService userService;

    @Autowired
    public CartController(CartListService cartListService, ItemService itemService, UserService userService) {
        this.cartListService = cartListService;
        this.itemService = itemService;
        this.userService = userService;
    }

    // HTML을 제공하기 위한 부분 ⬇ ==================================================

    @GetMapping("/cart")
    public String cart(Model model) {
        User user = userService.getUserByEmail("관리자");
        CartList cartList = cartListService.getCartItems(user);
        List<CartItem> cartItems = cartList.getCartItems();
        model.addAttribute("cartItems", cartItems);
        return "cart";
    }

    // 데이터를 받아와서 처리하기 위한 부분 ⬇ ==================================================

    // 장비구니에 상품 추가
    // TODO: 상품 추가시 기존 상품이 있다면 해당 상품의 카운트를 올려주는 기능
    @PostMapping("/addCart")
    public String addCart(@RequestBody BodyItem bodyItem) {
        // 유니크 값으로 아이템 조회 (없는 경우 예외 처리)
        Item item = itemService.findByUniqueValue(bodyItem.getUniqueValue());
        if (item == null) {
            return "redirect:/error?message=ItemNotFound"; // 에러 페이지 또는 메시지 전달
        }

        // 관리자 계정 조회 (없는 경우 예외 처리)
        User user = userService.getUserByEmail("관리자");
        if (user == null) {
            return "redirect:/error?message=UserNotFound"; // 에러 페이지로 이동
        }

        // 장바구니에 아이템 추가
        cartListService.addItem(user, item, 1, bodyItem.getPrice());

        // 장바구니 페이지로 이동
        return "redirect:/products"; // 또는 원래 페이지로 리디렉션
    }

    // TODO: 상품의 수량을 변경하는 기능

    // 장바구니에서 상품 삭제
    @PostMapping("/deleteCartItem")
    public String deleteCartItem(@RequestBody DeleteCartItemRequest request) {
        // "관리자" 이메일로 사용자 정보 가져오기
        User user = userService.getUserByEmail("관리자");

        // 아이템 삭제
        ResultMessage result = cartListService.deleteItemById(user, request.getCartItemId());

        // 삭제 결과 반환
        return "redirect:/cart";
    }
}
