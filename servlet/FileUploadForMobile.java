package com.chcgp.cloud.servlet;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.util.HibernateUtil;

import com.chcgp.cloud.dao.File;
import com.chcgp.cloud.dao.HibernateDao;
import com.chcgp.cloud.rest.RestUtils;
import com.chcgp.config.ConfigPropertiesExposerListener;
import com.chcgp.core.Constants;
import com.chcgp.core.util.OSSUtility;
import com.chcgp.core.util.Utils;

@WebServlet(name = "MarketFileUploadForMobile", urlPatterns = {"/marketfileuploadformobile"})
public class MarketFileUploadForMobile extends HttpServlet {

	private static final long serialVersionUID = -5899663574441942451L;
	protected Logger LOGGER = Logger.getLogger(getClass());
	
    public MarketFileUploadForMobile() {
        super();
    }

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		LOGGER.info("Get is not supported for File Upload.");
		throw new ServletException("Get is not supported for File Upload.");
	}
	
	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		processRequest(request, response);
	}

	protected void processRequest(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html;charset=UTF-8");
		
		String userName = request.getParameter("user");
		String stamp = request.getParameter("stamp");
		String sig = request.getParameter("sig");	
		String appointment = request.getParameter("appointment");	
		String filename = request.getParameter("filename");	
		String filesize = request.getParameter("filesize");	
		String filedescription = request.getParameter("filedescription");	
		
		Utils.logRequest(request);
		LOGGER.info("authing fileUpload("+userName+", "+stamp+", "+sig+")");
		RestUtils.authenticationByUserName(request, userName, stamp, sig);
		String result = "success";
		PrintWriter writer = response.getWriter();
		
		try {
			if(StringUtils.isNotBlank(appointment) && StringUtils.isNotBlank(filename)){
				Map configProperties = (Map) request.getServletContext().getAttribute(ConfigPropertiesExposerListener.DEFAULT_CONTEXT_PROPERTY);
				String folderName = Utils.getChcProperties().getProperty("cloud_root") + "/" + appointment;
				final String fileName = URLDecoder.decode(filename,"UTF-8");
				List<File> folders = HibernateDao.findByProperty(File.class, "marketAppointmentID", appointment);
				File folder;
				Set<File> children;
				if(folders.size() > 0){
					folder = folders.get(0);
					children = folder.getFiles();
				}else{
					folder = new File();
					folder.setMarketAppointmentID(appointment);
					folder.setFileSystem(Constants.FILESYSTEM_DIRECTORY);				
					folder.setFolderName(folderName);
					children = new HashSet<File>();
					folder.setFiles(children);
				}
				File newfile = new File();
				newfile.setFileName(fileName);
				newfile.setDesc(URLDecoder.decode(filedescription,"UTF-8"));
				newfile.setSize(Long.parseLong(filesize));
				newfile.setFileSystem(Constants.FILESYSTEM_FILE);
				newfile.setFolderName(folderName);
				newfile.setInputDate(new Date());
				newfile.setLocalhost(configProperties.get("local.server.host").toString());
				newfile.setCloudReady(Constants.FILE_CLOUD_NOT_READY);
				newfile.setCategory(Constants.CLOUD_IMAGE_FILE_CATEGORY_3);
				HibernateDao.saveOrUpdate(newfile);				
				children.add(newfile);
				HibernateDao.saveOrUpdate(folder);
				
				String extension ="";
				int i = fileName.lastIndexOf('.');
				if (i > 0) {
				    extension = fileName.substring(i);
				}			
				final String fileId = newfile.getId();
				final String uploadedFileLocation = Utils.padFileName(folderName, fileId+extension);

		        int read = 0;
		        byte[] bytes = new byte[1024];

		        java.io.File target = new java.io.File(uploadedFileLocation);
				if(!target.getParentFile().exists()){
					target.getParentFile().mkdirs();
				}
			    InputStream uploadedInputStream = request.getInputStream();  
				OutputStream out = new FileOutputStream(target);
		        while ((read = uploadedInputStream.read(bytes)) != -1) {
		            out.write(bytes, 0, read);
		        }
		        
		        LOGGER.info("File "+fileName+" being uploaded to "+uploadedFileLocation);
				
				Thread aliyunThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							LOGGER.info("convertThread["+uploadedFileLocation+"] START ...");
							java.io.File uploadedFile = new java.io.File(uploadedFileLocation);
							OSSUtility.putFile(OSSUtility.BUCKET_PUB, Constants.MARKET_FILE + fileId+"/"+ fileName, uploadedFile, fileName, true);
							File localFile = HibernateDao.getById(com.chcgp.cloud.dao.File.class, fileId);
							localFile.setCloudReady(Constants.FILE_CLOUD_READY);
							HibernateDao.saveOrUpdate(localFile);
							HibernateUtil.closeSession();
							LOGGER.info("convertThread["+uploadedFileLocation+"] DONE");
						} catch (Exception e) {
							LOGGER.info(e.getMessage(), e);
						}
					}					
				}, "aliyunThread["+uploadedFileLocation+"]");
				
				aliyunThread.start();
				out.flush();
		        out.close();
				result = "File uploaded. Name: " + fileName +". Size: "+filesize +". ID: "+fileId;
			}else{
				result = "预约id为空，或者文件名为空";
			}
			writer.print(result);
		} catch (Exception e) {
			LOGGER.info(e.getMessage(), e);
			result = e.getMessage();
			writer.print(result);
		} finally {
			try {
				HibernateUtil.closeSession();
			} catch (HibernateException e) {
				LOGGER.info(e.getMessage(), e);
			}
		}
	    
	}

}
