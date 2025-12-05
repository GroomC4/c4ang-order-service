package com.groom.order.common.exception.handler

import com.groom.order.common.exception.AuthenticationException
import com.groom.order.common.exception.ErrorCode
import com.groom.order.common.exception.OrderException
import com.groom.order.common.exception.PermissionException
import com.groom.order.common.exception.ProductException
import com.groom.order.common.exception.RefreshTokenException
import com.groom.order.common.exception.ResourceException
import com.groom.order.common.exception.StoreException
import com.groom.order.common.exception.UserException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(value = [IllegalArgumentException::class])
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                code = ErrorCode.INVALID_REQUEST_PARAMETER,
                message = e.message ?: "잘못된 요청입니다.",
            ),
            HttpStatus.BAD_REQUEST,
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(
                code = ErrorCode.INVALID_REQUEST_STATE,
                message = e.message ?: "요청을 처리하는데 실패하였습니다.",
            ),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )

    /**
     * 인증 관련 예외 처리
     *
     * 발생 위치:
     * - CustomerAuthenticationService.login(): UserNotFoundByEmail, InvalidPassword
     * - OwnerAuthenticationService.login(): UserNotFoundByEmail, InvalidPassword
     *
     * 참고: TokenException은 필터 레이어에서만 발생하므로 CustomAuthenticationEntryPoint에서 처리
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException): ResponseEntity<ErrorResponse> {
        val errorCode =
            when (e) {
                is AuthenticationException.UserNotFoundByEmail -> {
                    logger.warn(e) { "User not found by email: ${e.email}" }
                    ErrorCode.USER_NOT_FOUND_BY_EMAIL
                }

                is AuthenticationException.InvalidPassword -> {
                    logger.warn(e) { "Invalid password for email: ${e.email}" }
                    ErrorCode.INVALID_PASSWORD
                }

                is AuthenticationException.InvalidCredentials -> {
                    logger.warn(e) { "Invalid credentials: clue=${e.clue}" }
                    ErrorCode.INVALID_CREDENTIALS
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "인증에 실패하였습니다.",
            ),
            HttpStatus.UNAUTHORIZED,
        )
    }

    /**
     * 리프레시 토큰 관련 예외 처리
     *
     * 발생 위치:
     * - RefreshTokenService.refresh() → Authenticator.refreshCredentials()
     *   - RefreshTokenNotFound: DB에 리프레시 토큰이 없는 경우
     *   - RefreshTokenExpired: 리프레시 토큰 만료
     */
    @ExceptionHandler(RefreshTokenException::class)
    fun handleRefreshTokenException(e: RefreshTokenException): ResponseEntity<ErrorResponse> {
        val errorCode =
            when (e) {
                is RefreshTokenException.RefreshTokenNotFound -> {
                    logger.warn(e) { "Refresh token not found" }
                    ErrorCode.REFRESH_TOKEN_NOT_FOUND
                }

                is RefreshTokenException.RefreshTokenExpired -> {
                    logger.warn(e) { "Refresh token expired" }
                    ErrorCode.REFRESH_TOKEN_EXPIRED
                }

                is RefreshTokenException.RefreshTokenMismatch -> {
                    logger.warn(e) { "Refresh token mismatch for userId: ${e.userId}" }
                    ErrorCode.REFRESH_TOKEN_MISMATCH
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "리프레시 토큰 검증에 실패하였습니다.",
            ),
            HttpStatus.UNAUTHORIZED,
        )
    }

    /**
     * 사용자 관련 예외 처리
     *
     * 발생 위치:
     * - UserPolicy.checkAlreadyRegister(): DuplicateEmail
     * - CustomerAuthenticationService.logout(): UserNotFound
     * - OwnerAuthenticationService.logout(): UserNotFound
     * - StoreController.registerStore(): InsufficientPermission
     */
    @ExceptionHandler(UserException::class)
    fun handleUserException(e: UserException): ResponseEntity<ErrorResponse> {
        val (errorCode, httpStatus) =
            when (e) {
                is UserException.DuplicateEmail -> {
                    logger.warn(e) { "Duplicate email: ${e.email}" }
                    ErrorCode.DUPLICATE_EMAIL to HttpStatus.CONFLICT
                }

                is UserException.UserNotFound -> {
                    logger.warn(e) { "User not found: ${e.userId}" }
                    ErrorCode.USER_NOT_FOUND to HttpStatus.NOT_FOUND
                }

                is UserException.UserAlreadyExists -> {
                    logger.warn(e) { "User already exists: ${e.identifier}" }
                    ErrorCode.USER_ALREADY_EXISTS to HttpStatus.CONFLICT
                }

                is UserException.InsufficientPermission -> {
                    logger.warn(e) { "Insufficient permission: userId=${e.userId}, required=${e.requiredRole}, current=${e.currentRole}" }
                    ErrorCode.INSUFFICIENT_PERMISSION to HttpStatus.FORBIDDEN
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "사용자 처리 중 오류가 발생하였습니다.",
            ),
            httpStatus,
        )
    }

    @ExceptionHandler(PermissionException::class)
    fun handlePermissionException(e: PermissionException): ResponseEntity<ErrorResponse> {
        val errorCode =
            when (e) {
                is PermissionException.AccessDenied -> {
                    logger.warn(e) { "Access denied: userId=${e.userId}, resource=${e.resource}" }
                    ErrorCode.ACCESS_DENIED
                }

                is PermissionException.InsufficientPermissions -> {
                    logger.warn(e) { "Insufficient permissions: userId=${e.userId}, required=${e.required}" }
                    ErrorCode.INSUFFICIENT_PERMISSIONS
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "권한이 부족합니다.",
            ),
            HttpStatus.FORBIDDEN,
        )
    }

    @ExceptionHandler(ResourceException::class)
    fun handleResourceException(e: ResourceException): ResponseEntity<ErrorResponse> {
        val (errorCode, httpStatus) =
            when (e) {
                is ResourceException.ResourceNotFound -> {
                    logger.warn(e) { "Resource not found: type=${e.resourceType}, id=${e.identifier}, clue=${e.clue}" }
                    ErrorCode.RESOURCE_NOT_FOUND to HttpStatus.NOT_FOUND
                }

                is ResourceException.ResourceAlreadyExists -> {
                    logger.warn(e) { "Resource already exists: type=${e.resourceType}, id=${e.identifier}, clue=${e.clue}" }
                    ErrorCode.RESOURCE_ALREADY_EXISTS to HttpStatus.CONFLICT
                }

                is ResourceException.ResourceConflict -> {
                    logger.warn(e) { "Resource conflict: type=${e.resourceType}, reason=${e.reason}, clue=${e.clue}" }
                    ErrorCode.RESOURCE_CONFLICT to HttpStatus.CONFLICT
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "리소스 처리 중 오류가 발생하였습니다.",
            ),
            httpStatus,
        )
    }

    /**
     * 스토어 관련 예외 처리
     *
     * 발생 위치:
     * - StorePolicy.checkStoreAlreadyExists(): DuplicateStore
     * - StorePolicy.checkStoreDeletable(): StoreNotFound, StoreAlreadyDeleted
     * - StorePolicy.checkStoreAccess(): StoreAccessDenied
     */
    @ExceptionHandler(StoreException::class)
    fun handleStoreException(e: StoreException): ResponseEntity<ErrorResponse> {
        val (errorCode, httpStatus) =
            when (e) {
                is StoreException.DuplicateStore -> {
                    logger.warn(e) { "Duplicate store for userId: ${e.ownerUserId}" }
                    ErrorCode.DUPLICATE_STORE to HttpStatus.BAD_REQUEST
                }

                is StoreException.StoreNotFound -> {
                    logger.warn(e) { "Store not found: ${e.storeId}" }
                    ErrorCode.STORE_NOT_FOUND to HttpStatus.NOT_FOUND
                }

                is StoreException.StoreAlreadyDeleted -> {
                    logger.warn(e) { "Store already deleted: ${e.storeId}" }
                    ErrorCode.STORE_ALREADY_DELETED to HttpStatus.BAD_REQUEST
                }

                is StoreException.StoreAlreadySuspended -> {
                    logger.warn(e) { "Store already suspended: ${e.storeId}" }
                    ErrorCode.STORE_ALREADY_SUSPENDED to HttpStatus.BAD_REQUEST
                }

                is StoreException.InvalidStoreStatusTransition -> {
                    logger.warn(
                        e,
                    ) { "Invalid store status transition: storeId=${e.storeId}, from=${e.currentStatus}, to=${e.targetStatus}" }
                    ErrorCode.INVALID_STORE_STATUS_TRANSITION to HttpStatus.BAD_REQUEST
                }

                is StoreException.StoreAccessDenied -> {
                    logger.warn(e) { "Store access denied: storeId=${e.storeId}, userId=${e.userId}" }
                    ErrorCode.STORE_ACCESS_DENIED to HttpStatus.FORBIDDEN
                }

                is StoreException.CannotUpdateSuspendedStore -> {
                    logger.warn(e) { "Cannot update suspended store: ${e.storeId}" }
                    ErrorCode.CANNOT_UPDATE_SUSPENDED_STORE to HttpStatus.BAD_REQUEST
                }

                is StoreException.CannotUpdateDeletedStore -> {
                    logger.warn(e) { "Cannot update deleted store: ${e.storeId}" }
                    ErrorCode.CANNOT_UPDATE_DELETED_STORE to HttpStatus.BAD_REQUEST
                }

                is StoreException.StoreRegistrationRequired -> {
                    logger.warn(e) { "store registration required: ${e.storeId}" }
                    ErrorCode.STORE_REGISTRATION_REQUIRED to HttpStatus.NOT_FOUND
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "스토어 처리 중 오류가 발생하였습니다.",
            ),
            httpStatus,
        )
    }

    /**
     * 상품 관련 예외 처리
     *
     * 발생 위치:
     * - GetProductDetailService.getProductDetail(): ProductNotFound
     */
    @ExceptionHandler(ProductException::class)
    fun handleProductException(e: ProductException): ResponseEntity<ErrorResponse> {
        val (errorCode, httpStatus) =
            when (e) {
                is ProductException.ProductNotFound -> {
                    logger.warn(e) { "Product not found: ${e.productId}" }
                    ErrorCode.PRODUCT_NOT_FOUND to HttpStatus.NOT_FOUND
                }

                is ProductException.ProductAccessDenied -> {
                    logger.warn(e) { "Product access denied: productId=${e.productId}, userId=${e.userId}" }
                    ErrorCode.PRODUCT_ACCESS_DENIED to HttpStatus.FORBIDDEN
                }

                is ProductException.ProductAlreadyDeleted -> {
                    logger.warn(e) { "Product already deleted: ${e.productId}" }
                    ErrorCode.PRODUCT_ALREADY_DELETED to HttpStatus.BAD_REQUEST
                }

                is ProductException.ProductAlreadyHidden -> {
                    logger.warn(e) { "Product already hidden: ${e.productId}" }
                    ErrorCode.PRODUCT_ALREADY_HIDDEN to HttpStatus.BAD_REQUEST
                }

                is ProductException.DuplicateProductName -> {
                    logger.warn(e) { "Duplicate product name: ${e.name}" }
                    ErrorCode.DUPLICATE_PRODUCT_NAME to HttpStatus.BAD_REQUEST
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "상품 처리 중 오류가 발생하였습니다.",
            ),
            httpStatus,
        )
    }

    /**
     * 주문 관련 예외 처리
     *
     * 발생 위치:
     * - CreateOrderService.createOrder(): DuplicateOrderRequest, InsufficientStock, StoreClosed
     * - GetOrderDetailService.getOrderDetail(): OrderNotFound, OrderAccessDenied
     * - CancelOrderService.cancelOrder(): OrderNotFound, CannotCancelOrder
     * - RefundOrderService.refundOrder(): OrderNotFound, CannotRefundOrder
     */
    @ExceptionHandler(OrderException::class)
    fun handleOrderException(e: OrderException): ResponseEntity<ErrorResponse> {
        val (errorCode, httpStatus) =
            when (e) {
                is OrderException.DuplicateOrderRequest -> {
                    logger.warn(e) { "Duplicate order request: ${e.idempotencyKey}" }
                    ErrorCode.DUPLICATE_ORDER_REQUEST to HttpStatus.CONFLICT
                }

                is OrderException.InsufficientStock -> {
                    logger.warn(e) { "Insufficient stock: storeId=${e.storeId}" }
                    ErrorCode.INSUFFICIENT_STOCK to HttpStatus.CONFLICT // 409 - 재고 부족(리소스 상태 충돌)
                }

                is OrderException.StoreClosed -> {
                    logger.warn(e) { "Store closed: storeId=${e.storeId}" }
                    ErrorCode.STORE_CLOSED to HttpStatus.BAD_REQUEST
                }

                is OrderException.OrderNotFound -> {
                    logger.warn(e) { "Order not found: ${e.orderId}" }
                    ErrorCode.ORDER_NOT_FOUND to HttpStatus.NOT_FOUND
                }

                is OrderException.CannotCancelOrder -> {
                    logger.warn(e) { "Cannot cancel order: orderId=${e.orderId}, status=${e.currentStatus}" }
                    ErrorCode.CANNOT_CANCEL_ORDER to HttpStatus.CONFLICT // 409 - 리소스 상태 충돌
                }

                is OrderException.CannotRefundOrder -> {
                    logger.warn(e) { "Cannot refund order: orderId=${e.orderId}, status=${e.currentStatus}" }
                    ErrorCode.CANNOT_REFUND_ORDER to HttpStatus.BAD_REQUEST
                }

                is OrderException.OrderAccessDenied -> {
                    logger.warn(e) { "Order access denied: orderId=${e.orderId}, userId=${e.userId}" }
                    ErrorCode.ORDER_ACCESS_DENIED to HttpStatus.FORBIDDEN
                }

                is OrderException.OrderStatusInvalid -> {
                    logger.warn(e) { "Order status invalid: orderId=${e.orderId}, current=${e.currentStatus}, required=${e.requiredStatus}" }
                    ErrorCode.ORDER_STATUS_INVALID to HttpStatus.CONFLICT
                }

                is OrderException.PaymentAlreadyExists -> {
                    logger.warn(e) { "Payment already exists: orderId=${e.orderId}, existingPaymentId=${e.existingPaymentId}" }
                    ErrorCode.PAYMENT_ALREADY_EXISTS to HttpStatus.CONFLICT
                }
            }

        return ResponseEntity(
            ErrorResponse(
                code = errorCode,
                message = e.message ?: "주문 처리 중 오류가 발생하였습니다.",
            ),
            httpStatus,
        )
    }
}
