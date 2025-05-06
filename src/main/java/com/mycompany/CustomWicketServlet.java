package com.mycompany;

import java.io.IOException;
import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.protocol.http.WicketServlet;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@MultipartConfig(maxFileSize = Long.MAX_VALUE, maxRequestSize = Long.MAX_VALUE, fileSizeThreshold = 3000)
public class CustomWicketServlet extends WicketServlet {

	@Override
	protected WicketFilter newWicketFilter() {
		return new WicketFilter() {

			@Override
			protected boolean processRequestCycle(RequestCycle requestCycle, WebResponse webResponse, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain chain) throws IOException, ServletException {
				// for reading some parameter for ech request. For POST, request thi wil force rearing the part
				//boolean slowRequest = requestCycle.getRequest().getRequestParameters().getParameterValue("slowRequest").toBoolean(false);
				return super.processRequestCycle(requestCycle, webResponse, httpServletRequest, httpServletResponse, chain);
			}
		};
	}
}
