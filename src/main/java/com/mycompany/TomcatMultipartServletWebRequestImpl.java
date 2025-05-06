/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycompany;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.catalina.core.ApplicationPart;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.commons.fileupload2.core.FileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadByteCountLimitException;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet5.JakartaServletFileUpload;
//import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.ProgressListener;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.apache.wicket.Application;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.protocol.http.servlet.MultipartServletWebRequest;
import org.apache.wicket.protocol.http.servlet.UploadInfo;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.lang.Bytes;
import org.apache.wicket.util.string.StringValue;
import org.apache.wicket.util.value.ValueMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

/**
 * Servlet specific WebRequest subclass for multipart content uploads.
 *
 * @author Jonathan Locke
 * @author Eelco Hillenius
 * @author Cameron Braid
 * @author Ate Douma
 * @author Igor Vaynberg (ivaynberg)
 */
public class TomcatMultipartServletWebRequestImpl extends MultipartServletWebRequest
{
	/** Map of file items. */
	private final Map<String, List<FileItem>> files;

	/** Map of parameters. */
	private final ValueMap parameters;

	private final String upload;

	/**
	 * total bytes uploaded (downloaded from server's pov) so far. used for upload notifications
	 */
	private int bytesUploaded;

	/** content length cache, used for upload notifications */
	private int totalBytes;

	/**
	 * Constructor
	 *
	 * @param request
	 *            the servlet request
	 * @param filterPrefix
	 *            prefix to wicket filter mapping
	 * @param maxSize
	 *            the maximum size allowed for this request
	 * @param upload
	 *            upload identifier for {@link UploadInfo}
	 *
	 * @throws FileUploadException
	 *             Thrown if something goes wrong with upload
	 */
	public TomcatMultipartServletWebRequestImpl(HttpServletRequest request, String filterPrefix,
												Bytes maxSize, String upload) throws FileUploadException
	{
		super(request, filterPrefix);

		Args.notNull(upload, "upload");
		this.upload = upload;
		parameters = new ValueMap();
		files = new HashMap<>();

		// Check that request is multipart
		final boolean isMultipart = JakartaServletFileUpload.isMultipartContent(request);
		if (!isMultipart)
		{
			throw new IllegalStateException(
				"ServletRequest does not contain multipart content. One possible solution is to explicitly call Form.setMultipart(true), Wicket tries its best to auto-detect multipart forms but there are certain situations where it cannot.");
		}

		setMaxSize(maxSize);
	}

	// LOOK for references in wicket code to this. In particular AbstractFileUploadResource, look that before calling this method we are accessing a parameter uploadId... this is what triggers tomcat parsing multipart
	// and the exhaustion of the Stream, thus we ge no "files" here.
	@Override
	public void parseFileParts() throws FileUploadException
	{
		HttpServletRequest request = getContainerRequest();

		// The encoding that will be used to decode the string parameters
		// It should NOT be null at this point, but it may be
		// especially if the older Servlet API 2.2 is used
		String encoding = request.getCharacterEncoding();

		// The encoding can also be null when using multipart/form-data encoded forms.
		// In that case, we use the [application-encoding] which we always demand using
		// the attribute 'accept-encoding' in wicket forms.
		if (encoding == null)
		{
			encoding = Application.get().getRequestCycleSettings().getResponseRequestEncoding();
		}

		File location = new File(System.getProperty("java.io.tmpdir"));
		// Create a new file upload handler
		org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory factory = new org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory();
		factory.setRepository(location);

		FileUpload upload = new FileUpload();
		if (wantUploadProgressUpdates()) {
			upload.setProgressListener(new ProgressListener() {
				@Override
				public void update(long pBytesRead, long pContentLength, int pItems) {
					onUploadUpdate(pBytesRead, pContentLength);
				}
			});
			totalBytes = request.getContentLength();
			onUploadStarted(totalBytes);
		}
		upload.setFileItemFactory(factory);
		upload.setFileSizeMax(getMaxSize().bytes());
		upload.setSizeMax(getMaxSize().bytes());

		List<FileItem> items = new ArrayList<>();

		List<Part> parts = new ArrayList<>();
		try {

			List<org.apache.tomcat.util.http.fileupload.FileItem> items1 = upload.parseRequest(new ServletRequestContext(request));
			int maxPostSize = Integer.MAX_VALUE;
			int postSize = 0;
			Charset charset = getCharset();
			for (org.apache.tomcat.util.http.fileupload.FileItem item : items1) {
				ApplicationPart part = new ApplicationPart(item, location);
				parts.add(part);
				FileItem fileItem = new ServletPartFileItem(part);
				items.add(fileItem);
				if (part.getSubmittedFileName() == null) {
					String name = part.getName();
					if (maxPostSize >= 0) {
						// Have to calculate equivalent size. Not completely
						// accurate but close enough.
						postSize += name.getBytes(charset).length;
						// Equals sign
						postSize++;
						// Value length
						postSize += (int) part.getSize();
						// Value separator
						postSize++;
						if (postSize > maxPostSize) {
							throw new IllegalStateException("coyoteRequest.maxPostSizeExceeded");
						}
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		finally
		{
			if (wantUploadProgressUpdates()) {
				onUploadCompleted();
			}
		}

		// Loop through items
		for (final FileItem item : items)
		{
			// Get next item
			// If item is a form field
			if (item.isFormField())
			{
				// Set parameter value
				final String value;
				if (encoding != null)
				{
					try
					{
						value = item.getString(Charset.forName(encoding));
					}
					catch (IOException e)
					{
						throw new WicketRuntimeException(e);
					}
				}
				else
				{
					value = item.getString();
				}

				addParameter(item.getFieldName(), value);
			}
			else
			{
				List<FileItem> fileItems = files.get(item.getFieldName());
				if (fileItems == null)
				{
					fileItems = new ArrayList<>();
					files.put(item.getFieldName(), fileItems);
				}
				// Add to file list
				fileItems.add(item);
			}
		}
	}

    /**
	 * Adds a parameter to the parameters value map
	 *
	 * @param name
	 *            parameter name
	 * @param value
	 *            parameter value
	 */
	private void addParameter(final String name, final String value)
	{
		final String[] currVal = (String[])parameters.get(name);

		String[] newVal;

		if (currVal != null)
		{
			newVal = new String[currVal.length + 1];
			System.arraycopy(currVal, 0, newVal, 0, currVal.length);
			newVal[currVal.length] = value;
		}
		else
		{
			newVal = new String[] { value };

		}

		parameters.put(name, newVal);
	}

	/**
	 * @return Returns the files.
	 */
	@Override
	public Map<String, List<FileItem>> getFiles()
	{
		return files;
	}

	/**
	 * Gets the file that was uploaded using the given field name.
	 *
	 * @param fieldName
	 *            the field name that was used for the upload
	 * @return the upload with the given field name
	 */
	@Override
	public List<FileItem> getFile(final String fieldName)
	{
		return files.get(fieldName);
	}

	@Override
	protected Map<String, List<StringValue>> generatePostParameters()
	{
		Map<String, List<StringValue>> res = new HashMap<>();
		for (Map.Entry<String, Object> entry : parameters.entrySet())
		{
			String key = entry.getKey();
			String[] val = (String[])entry.getValue();
			if (val != null && val.length > 0)
			{
				List<StringValue> items = new ArrayList<>();
				for (String s : val)
				{
					items.add(StringValue.valueOf(s));
				}
				res.put(key, items);
			}
		}
		return res;
	}

	/**
	 * Subclasses that want to receive upload notifications should return true. By default, it takes
	 * the value from {@link org.apache.wicket.settings.ApplicationSettings#isUploadProgressUpdatesEnabled()}.
	 *
	 * @return true if upload status update event should be invoked
	 */
	protected boolean wantUploadProgressUpdates()
	{
		return Application.get().getApplicationSettings().isUploadProgressUpdatesEnabled();
	}

	/**
	 * Upload start callback
	 *
	 * @param totalBytes
	 */
	protected void onUploadStarted(int totalBytes)
	{
		UploadInfo info = new UploadInfo(totalBytes);

		setUploadInfo(getContainerRequest(), upload, info);
	}

	/**
	 * Upload status update callback
	 *
	 * @param bytesUploaded
	 * @param total
	 */
	protected void onUploadUpdate(long bytesUploaded, long total)
	{
		HttpServletRequest request = getContainerRequest();
		UploadInfo info = getUploadInfo(request, upload);
		if (info == null)
		{
			throw new IllegalStateException(
				"could not find UploadInfo object in session which should have been set when uploaded started");
		}
		info.setBytesUploaded(bytesUploaded);

		setUploadInfo(request, upload, info);
	}

	/**
	 * Upload completed callback
	 */
	protected void onUploadCompleted()
	{
		clearUploadInfo(getContainerRequest(), upload);
	}

	/**
	 * An {@link InputStream} that updates total number of bytes read
	 *
	 * @author Igor Vaynberg (ivaynberg)
	 */
	private class CountingInputStream extends InputStream
	{

		private final InputStream in;

		/**
		 * Constructs a new CountingInputStream.
		 *
		 * @param in
		 *            InputStream to delegate to
		 */
		public CountingInputStream(InputStream in)
		{
			this.in = in;
		}

		@Override
		public int read() throws IOException
		{
			int read = in.read();
			bytesUploaded += (read < 0) ? 0 : 1;
			onUploadUpdate(bytesUploaded, totalBytes);
			return read;
		}

		@Override
		public int read(byte[] b) throws IOException
		{
			int read = in.read(b);
			bytesUploaded += (read < 0) ? 0 : read;
			onUploadUpdate(bytesUploaded, totalBytes);
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			int read = in.read(b, off, len);
			bytesUploaded += (read < 0) ? 0 : read;
			onUploadUpdate(bytesUploaded, totalBytes);
			return read;
		}

	}

	@Override
	public MultipartServletWebRequest newMultipartWebRequest(Bytes maxSize, String upload)
		throws FileUploadException
	{
		// FIXME mgrigorov: Why these checks are made here ?!
		// Why they are not done also at org.apache.wicket.protocol.http.servlet.MultipartServletWebRequestImpl.newMultipartWebRequest(org.apache.wicket.util.lang.Bytes, java.lang.String, org.apache.wicket.util.upload.FileItemFactory)() ?
		// Why there is no check that the summary of all files' sizes is less than the set maxSize ?
		// Setting a breakpoint here never breaks with the standard upload examples.

		Bytes fileMaxSize = getFileMaxSize();
		for (Map.Entry<String, List<FileItem>> entry : files.entrySet())
		{
			List<FileItem> fileItems = entry.getValue();
			for (FileItem fileItem : fileItems)
			{
				if (fileMaxSize != null && fileItem.getSize() > fileMaxSize.bytes())
				{
					String fieldName = entry.getKey();
					FileUploadException fslex = new FileUploadByteCountLimitException("The field '" +
							fieldName + "' exceeds its maximum permitted size of '" +
							maxSize + "' characters.", fileItem.getSize(), fileMaxSize.bytes(), fileItem.getName(), fieldName);
					throw fslex;
				}
			}
		}
		return this;
	}

	@Override
	public MultipartServletWebRequest newMultipartWebRequest(Bytes maxSize, String upload, FileItemFactory factory)
			throws FileUploadException
	{
		return this;
	}

	private static final String SESSION_KEY = TomcatMultipartServletWebRequestImpl.class.getName();

	private static String getSessionKey(String upload)
	{
		return SESSION_KEY + ":" + upload;
	}

	/**
	 * Retrieves {@link UploadInfo} from session, null if not found.
	 *
	 * @param req
	 *            http servlet request, not null
	 * @param upload
	 *            upload identifier
	 * @return {@link UploadInfo} object from session, or null if not found
	 */
	public static UploadInfo getUploadInfo(final HttpServletRequest req, String upload)
	{
		Args.notNull(req, "req");
		return (UploadInfo)req.getSession().getAttribute(getSessionKey(upload));
	}

	/**
	 * Sets the {@link UploadInfo} object into session.
	 *
	 * @param req
	 *            http servlet request, not null
	 * @param upload
	 *            upload identifier
	 * @param uploadInfo
	 *            {@link UploadInfo} object to be put into session, not null
	 */
	public static void setUploadInfo(final HttpServletRequest req, String upload,
		final UploadInfo uploadInfo)
	{
		Args.notNull(req, "req");
		Args.notNull(upload, "upload");
		Args.notNull(uploadInfo, "uploadInfo");
		req.getSession().setAttribute(getSessionKey(upload), uploadInfo);
	}

	/**
	 * Clears the {@link UploadInfo} object from session if one exists.
	 *
	 * @param req
	 *            http servlet request, not null
	 * @param upload
	 *            upload identifier
	 */
	public static void clearUploadInfo(final HttpServletRequest req, String upload)
	{
		Args.notNull(req, "req");
		Args.notNull(upload, "upload");
		req.getSession().removeAttribute(getSessionKey(upload));
	}

}
