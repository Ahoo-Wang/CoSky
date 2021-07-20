/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.rest.security;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.rest.security.audit.AuditLogService;
import me.ahoo.cosky.rest.security.audit.AuditLog;
import me.ahoo.cosky.rest.security.rbac.Action;
import me.ahoo.cosky.rest.security.rbac.RBACService;
import me.ahoo.cosky.rest.security.user.User;
import me.ahoo.cosky.rest.support.RequestPathPrefix;
import me.ahoo.cosky.rest.support.Requests;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * @author ahoo wang
 */
@Slf4j
public class AuthorizeHandlerInterceptor implements HandlerInterceptor {
    public static final String AUTH_HEADER = "Authorization";

    private final RBACService rbacService;
    private final AuditLogService auditService;
    private final SecurityProperties securityProperties;

    public AuthorizeHandlerInterceptor(RBACService rbacService, AuditLogService auditService, SecurityProperties securityProperties) {
        this.rbacService = rbacService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }

    /**
     * Intercept the execution of a handler. Called after HandlerMapping determined
     * an appropriate handler object, but before HandlerAdapter invokes the handler.
     * <p>DispatcherServlet processes a handler in an execution chain, consisting
     * of any number of interceptors, with the handler itself at the end.
     * With this method, each interceptor can decide to abort the execution chain,
     * typically sending an HTTP error or writing a custom response.
     * <p><strong>Note:</strong> special considerations apply for asynchronous
     * request processing. For more details see
     * {@link AsyncHandlerInterceptor}.
     * <p>The default implementation returns {@code true}.
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  chosen handler to execute, for type and/or instance evaluation
     * @return {@code true} if the execution chain should proceed with the
     * next interceptor or the handler itself. Else, DispatcherServlet assumes
     * that this interceptor has already dealt with the response itself.
     * @throws Exception in case of errors
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getRequestURI().startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            return true;
        }
        if (HttpMethod.OPTIONS.name().equals(request.getMethod())) {
            return true;
        }
        String accessToken = request.getHeader(AUTH_HEADER);

        if (Strings.isNullOrEmpty(accessToken)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        try {
            if (!rbacService.authorize(accessToken, request, (HandlerMethod) handler)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }
        } catch (TokenExpiredException tokenExpiredException) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        return true;
    }


    /**
     * Intercept the execution of a handler. Called after HandlerAdapter actually
     * invoked the handler, but before the DispatcherServlet renders the view.
     * Can expose additional model objects to the view via the given ModelAndView.
     * <p>DispatcherServlet processes a handler in an execution chain, consisting
     * of any number of interceptors, with the handler itself at the end.
     * With this method, each interceptor can post-process an execution,
     * getting applied in inverse order of the execution chain.
     * <p><strong>Note:</strong> special considerations apply for asynchronous
     * request processing. For more details see
     * {@link AsyncHandlerInterceptor}.
     * <p>The default implementation is empty.
     *
     * @param request      current HTTP request
     * @param response     current HTTP response
     * @param handler      the handler (or {@link HandlerMethod}) that started asynchronous
     *                     execution, for type and/or instance examination
     * @param modelAndView the {@code ModelAndView} that the handler returned
     *                     (can also be {@code null})
     * @throws Exception in case of errors
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    /**
     * Callback after completion of request processing, that is, after rendering
     * the view. Will be called on any outcome of handler execution, thus allows
     * for proper resource cleanup.
     * <p>Note: Will only be called if this interceptor's {@code preHandle}
     * method has successfully completed and returned {@code true}!
     * <p>As with the {@code postHandle} method, the method will be invoked on each
     * interceptor in the chain in reverse order, so the first interceptor will be
     * the last to be invoked.
     * <p><strong>Note:</strong> special considerations apply for asynchronous
     * request processing. For more details see
     * {@link AsyncHandlerInterceptor}.
     * <p>The default implementation is empty.
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  the handler (or {@link HandlerMethod}) that started asynchronous
     *                 execution, for type and/or instance examination
     * @param ex       any exception thrown on handler execution, if any; this does not
     *                 include exceptions that have been handled through an exception resolver
     * @throws Exception in case of errors
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Action requestAction = Action.ofHttpMethod(request.getMethod());
        if (!securityProperties.getAuditLog().getAction().check(requestAction)) {
            return;
        }

        AuditLog auditLog = new AuditLog();
        if (!request.getRequestURI().startsWith(RequestPathPrefix.AUTHENTICATE_PREFIX)) {
            User currentUser = rbacService.getUserOfRequest(request);
            auditLog.setOperator(currentUser.getUsername());
        }

        auditLog.setResource(request.getRequestURI());
        auditLog.setAction(request.getMethod());
        auditLog.setIp(Requests.getRealIp(request));
        auditLog.setStatus(response.getStatus());

        if (Objects.nonNull(ex)) {
            auditLog.setMsg(ex.getMessage());
        }

        auditLog.setOpTime(System.currentTimeMillis());
        auditService.addLog(auditLog);
    }
}
