	@POST
	@Path("File/UploadOld")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public String uploadFileOld(
			@QueryParam("user") String userName,
			@QueryParam("stamp") String stamp,
			@QueryParam("sig") String sig,
			@Context HttpServletRequest request,
	        @FormDataParam("file") InputStream uploadedInputStream,
	        @FormDataParam("file") FormDataContentDisposition fileDetail,
	        @FormDataParam("appointment") String appointment,
	        @FormDataParam("filename") String filename,
	        @FormDataParam("filesize") String filesize,
	        @FormDataParam("filedescription") String filedescription) {
		Utils.logRequest(request);
		LOGGER.info("authing fileUpload("+userName+", "+stamp+", "+sig+")");
		RestUtils.authenticationByUserName(request, userName, stamp, sig);
		String result = "success";
		
		try {
			if(StringUtils.isNotBlank(appointment) && StringUtils.isNotBlank(fileDetail.getFileName())){
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
							OSSUtility.putFile(OSSUtility.BUCKET_PUB, Constants.MARKET_FILE + fileId+"/"+ fileName, uploadedFile);
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
			}
		} catch (Exception e) {
			LOGGER.info(e.getMessage(), e);
			result = e.getMessage();
		} finally {
			try {
				HibernateUtil.closeSession();
			} catch (HibernateException e) {
				LOGGER.info(e.getMessage(), e);
			}
		}
	    
		return result;
	}
