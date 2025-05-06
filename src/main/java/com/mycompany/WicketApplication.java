package com.mycompany;

import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.resource.FileUploadResourceReference;
import org.apache.wicket.markup.html.form.upload.resource.IUploadsFileManager;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.servlet.MultipartServletWebRequest;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.util.file.File;
import org.apache.wicket.util.lang.Bytes;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Application object for your web application.
 * If you want to run this application without deploying, run the Start class.
 */
public class WicketApplication extends WebApplication
{
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<? extends WebPage> getHomePage()
	{
		return HomePage.class;
	}

	/**
	 * @see org.apache.wicket.Application#init()
	 */
	@Override
	public void init()
	{
		super.init();

		// needed for the styling used by the quickstart
		getCspSettings().blocking()
			.add(CSPDirective.STYLE_SRC, CSPDirectiveSrcValue.SELF)
			.add(CSPDirective.STYLE_SRC, "https://fonts.googleapis.com/css")
			.add(CSPDirective.FONT_SRC, "https://fonts.gstatic.com");

		mountResource("/file-upload", FileUploadResourceReference.createNewInstance(new IUploadsFileManager() {
			@Override
			public void save(FileUpload fileItem, String uploadFieldId) {
				System.out.println("save " + fileItem);
			}

			@Override
			public File getFile(String uploadFieldId, String clientFileName) {
				return null;
			}
		}));
		getApplicationSettings().setUploadProgressUpdatesEnabled(true);
	}

	/*
	@Override
	public WebRequest newWebRequest(HttpServletRequest servletRequest, String filterPath) {
		return new ServletWebRequest(servletRequest, filterPath) {
			@Override
			public MultipartServletWebRequest newMultipartWebRequest(Bytes maxSize, String upload) throws FileUploadException {
				return new TomcatMultipartServletWebRequestImpl(getContainerRequest(), getFilterPrefix(), maxSize, upload);
			}
		};
	}
	 */
}
