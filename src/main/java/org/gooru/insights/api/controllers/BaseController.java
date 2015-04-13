package org.gooru.insights.api.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.util.IOUtils;
import org.gooru.insights.api.constants.ApiConstants;
import org.gooru.insights.api.constants.ApiConstants.apiHeaders;
import org.gooru.insights.api.constants.ApiConstants.fileAttributes;
import org.gooru.insights.api.constants.ApiConstants.modelAttributes;
import org.gooru.insights.api.models.RequestParamsDTO;
import org.gooru.insights.api.models.ResponseParamDTO;
import org.gooru.insights.api.spring.exception.BadRequestException;
import org.gooru.insights.api.utils.InsightsLogger;
import org.gooru.insights.api.utils.JsonDeserializer;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.servlet.ModelAndView;

import flexjson.JSONSerializer;

public class BaseController {
	
	private static final String REQUEST_DATA_ERROR = "Include data parameter!!!";
	
	private static final String REQUEST_BODY_ERROR = "Include JSON Body data!!!";
	
	private static final String TOKEN_HEADER_PREFIX = "Gooru-Session-Token";
	
	private static final String TOKEN_PARAM_PREFIX = "sessionToken";
	
	protected <K, V> ModelAndView getModel(List<Map<String, V>> data, Map<K, V> message) {

		ModelAndView model = new ModelAndView(modelAttributes.CONTENT.getAttribute());
		model.addObject(modelAttributes.CONTENT.getAttribute(), data);
		Map<String, Integer> filterDate = new HashMap<String, Integer>();
		Integer totalRows = null;
		if (message.containsKey(modelAttributes.TOTAL_ROWS.getAttribute())) {
			totalRows = message.get(modelAttributes.TOTAL_ROWS.getAttribute()) == null ? 0 : Integer.valueOf(message.get(modelAttributes.TOTAL_ROWS.getAttribute()).toString());
			message.remove(modelAttributes.TOTAL_ROWS.getAttribute());
			filterDate.put(modelAttributes.TOTAL_ROWS.getAttribute(), totalRows);
		}
		model.addObject(modelAttributes.PAGINATE.getAttribute(), filterDate);
		model.addObject(modelAttributes.MESSAGE.getAttribute(), message);
		return model;
	}

	public ModelAndView sendErrorResponse(HttpServletResponse response,Map<Integer,String> error) {

		ModelAndView model = new ModelAndView(modelAttributes.CONTENT.getAttribute());
		JSONObject resultJson = new JSONObject();
		for(Map.Entry<Integer,String> entry : error.entrySet()){
			response.setStatus(entry.getKey());
			response.setContentType(apiHeaders.JSON_HEADER.apiHeader());
			Map<String, Object> resultMap = new HashMap<String, Object>();
			try {
			resultMap.put(ApiConstants.STATUS_CODE, entry.getKey());
			resultMap.put(modelAttributes.MESSAGE.getAttribute(), entry.getValue());
			resultJson = new JSONObject(resultMap);
			response.setStatus(entry.getKey().intValue());
			response.getWriter().write(resultJson.toString());
			} catch (IOException e) {
				InsightsLogger.error("errorResponse", e);
			}
		}
		model.addObject(modelAttributes.MESSAGE.getAttribute(), resultJson);
		return model;
	}
	
	public ModelAndView getMailModel(String emailId) {

		ModelAndView model = new ModelAndView(modelAttributes.CONTENT.getAttribute());
		String message = ApiConstants.MAIL_TEXT;
		if (emailId != null && emailId != ApiConstants.STRING_EMPTY) {
			message.replace(ApiConstants.DEFAULT_MAIL, emailId);
		}
		return model.addObject(modelAttributes.MESSAGE.getAttribute(), message);
	}
	
	public ModelAndView getModel(Map<String, String> content) {
		
		ModelAndView model = new ModelAndView(modelAttributes.CONTENT.getAttribute());
		if(content != null && !content.isEmpty()){
			model.addObject(modelAttributes.CONTENT.getAttribute(), content);
		}else{
			model.addObject(modelAttributes.MESSAGE.getAttribute(), ApiConstants.DEFAULT_MAIL_MESSAGE);
		}
		return model;
	}
	
	public <M> ModelAndView getModel(ResponseParamDTO<M> data) {

		ModelAndView model = new ModelAndView(modelAttributes.VIEW_NAME.getAttribute());
		model.addObject(modelAttributes.RETURN_NAME.getAttribute() , new JSONSerializer().exclude(ApiConstants.EXCLUDE_CLASSES).deepSerialize(data));
		return model;
	}
	
	public void generateCSVOutput(HttpServletResponse response, File excelFile) throws IOException {
		
		InputStream sheet = new FileInputStream(excelFile);
		response.setContentType(apiHeaders.CSV_RESPONSE.apiHeader());
		IOUtils.copy(sheet, response.getOutputStream());
		response.getOutputStream().flush();
		response.getOutputStream().close();
	}
	
	public void generateExcelOutput(HttpServletResponse response, File excelFile) throws IOException {
		InputStream sheet = new FileInputStream(excelFile);
		response.setContentType("application/xls");
		IOUtils.copy(sheet, response.getOutputStream());
		response.getOutputStream().flush();
		response.getOutputStream().close();
	}

	public RequestParamsDTO deSerialize(String data) {
		return data != null ? new JsonDeserializer().deserialize(data, RequestParamsDTO.class) : null;
	}

	public String getTraceId(HttpServletRequest request) throws JSONException {
		Map<String, Object> requestParam = request.getParameterMap();
		JSONObject jsonObject = new JSONObject();
		for (Map.Entry<String, Object> entry : requestParam.entrySet()) {
			jsonObject.put(entry.getKey(), entry.getValue());
		}
		jsonObject.put("url", request.getRequestURL().toString());
		UUID uuid = UUID.randomUUID();
		InsightsLogger.debug(uuid.toString(), jsonObject.toString());
		request.setAttribute("traceId", uuid.toString());
		return uuid.toString();
	}

	public String getRequestData(HttpServletRequest request,String requestBody){
		if(request.getMethod().equalsIgnoreCase("GET")){
			if(request.getParameter("data") == null){
				throw new BadRequestException(REQUEST_DATA_ERROR);
			}
			return request.getParameter("data").toString();
		}else {
			if(requestBody == null){
				throw new BadRequestException(REQUEST_BODY_ERROR);
			}
			return requestBody;
		}
	}
	
	public HttpServletResponse setAllowOrigin(HttpServletResponse response) {
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Headers", "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With");
		response.setHeader("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE");
		return response;
	}
	
	public String getSessionToken(HttpServletRequest request) {

		if (request.getHeader(TOKEN_HEADER_PREFIX) != null) {
			return request.getHeader(TOKEN_HEADER_PREFIX);
		} else {
			return request.getParameter(TOKEN_PARAM_PREFIX);
		}
	}
	
	public String checkRequestContentType(HttpServletRequest request) {

		String fileExtension = fileAttributes.CSV.attribute();
		if (request.getHeader(apiHeaders.ACCEPT.apiHeader()).contains(apiHeaders.XLS_HEADER.apiHeader())) {
			fileExtension = fileAttributes.XLS.attribute();
		} else if (request.getHeader(apiHeaders.ACCEPT.apiHeader()).contains(apiHeaders.JSON_HEADER.apiHeader())) {
			fileExtension = fileAttributes.JSON.attribute();
		} 
		return fileExtension;
	}
}
