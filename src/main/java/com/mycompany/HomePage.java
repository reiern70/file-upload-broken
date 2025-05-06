package com.mycompany;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.extensions.ajax.markup.html.form.upload.UploadProgressBar;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.form.upload.resource.FileUploadToResourceField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.WebPage;

public class HomePage extends WebPage {

	private String text;

	private final FileUploadToResourceField fileUploadField;

	private final FeedbackPanel feedbackPanel;

	private final UploadProgressBar uploadProgressBar;

	public HomePage(final PageParameters parameters) {
		super(parameters);

		feedbackPanel = new FeedbackPanel("feedback");
		feedbackPanel.setOutputMarkupId(true);
		add(feedbackPanel);

		Form<Void> form = new Form<>("form");
		add(form);

		form.add(fileUploadField = new FileUploadToResourceField("file") {

			@Override
			protected void onUploadSuccess(AjaxRequestTarget target, List<UploadInfo> fileInfos) {
				feedbackPanel.success("Uploaded " + fileInfos.size() + " files");
				target.add(feedbackPanel);
			}

			@Override
			protected void onUploadFailure(AjaxRequestTarget target, String errorInfo) {
				feedbackPanel.success("Something went wrong");
				target.add(feedbackPanel);
			}
		});

		form.add(uploadProgressBar = new UploadProgressBar("progress", fileUploadField));
		form.add(new AjaxLink<Void>("submit") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				uploadProgressBar.start(target);
				fileUploadField.startUpload(target);
				target.add(feedbackPanel);
			}
		});
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
