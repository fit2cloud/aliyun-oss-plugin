package com.fit2cloud.jenkins.aliyunoss;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.ObjectMetadata;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.StringTokenizer;

public class AliyunOSSClient {
	private static final String fpSeparator = ";";

	public static boolean validateAliyunAccount(
			final String aliyunAccessKey, final String aliyunSecretKey) throws AliyunOSSException {
		try {
			OSSClient client = new OSSClient(aliyunAccessKey, aliyunSecretKey);
			client.listBuckets();
		} catch (Exception e) {
			throw new AliyunOSSException("阿里云账号验证失败：" + e.getMessage());
		}
		return true;
	}


	public static boolean validateOSSBucket(String aliyunAccessKey,
											String aliyunSecretKey, String bucketName) throws AliyunOSSException{
		try {
			OSSClient client = new OSSClient(aliyunAccessKey, aliyunSecretKey);
			client.getBucketLocation(bucketName);
		} catch (Exception e) {
			throw new AliyunOSSException("验证Bucket名称失败：" + e.getMessage());
		}
		return true;
	}

	public static int upload(AbstractBuild<?, ?> build, BuildListener listener,
							 final String aliyunAccessKey, final String aliyunSecretKey, final String aliyunEndPointSuffix, String bucketName,String expFP,String expVP) throws AliyunOSSException {
		OSSClient client = new OSSClient(aliyunAccessKey, aliyunSecretKey);
		String location = client.getBucketLocation(bucketName);
		String endpoint = "http://" + location + aliyunEndPointSuffix;
		client = new OSSClient(endpoint, aliyunAccessKey, aliyunSecretKey);
		int filesUploaded = 0; // Counter to track no. of files that are uploaded
		try {
			FilePath workspacePath = build.getWorkspace();
			if (workspacePath == null) {
				listener.getLogger().println("工作空间中没有任何文件.");
				return filesUploaded;
			}
			StringTokenizer strTokens = new StringTokenizer(expFP, fpSeparator);
			FilePath[] paths = null;

			listener.getLogger().println("开始上传到阿里云OSS...");
			listener.getLogger().println("上传endpoint是：" + endpoint);

			while (strTokens.hasMoreElements()) {
				String fileName = strTokens.nextToken();
				String embeddedVP = null;
				if (fileName != null) {
					int embVPSepIndex = fileName.indexOf("::");
					if (embVPSepIndex != -1) {
						if (fileName.length() > embVPSepIndex + 1) {
							embeddedVP = fileName.substring(embVPSepIndex + 2, fileName.length());
							if (Utils.isNullOrEmpty(embeddedVP)) {
								embeddedVP = null;
							}
							if (embeddedVP != null	&& !embeddedVP.endsWith(Utils.FWD_SLASH)) {
								embeddedVP = embeddedVP + Utils.FWD_SLASH;
							}
						}
						fileName = fileName.substring(0, embVPSepIndex);
					}
				}

				if (Utils.isNullOrEmpty(fileName)) {
					return filesUploaded;
				}

				FilePath fp = new FilePath(workspacePath, fileName);

				if (fp.exists() && !fp.isDirectory()) {
					paths = new FilePath[1];
					paths[0] = fp;
				} else {
					paths = workspacePath.list(fileName);
				}

				if (paths.length != 0) {
					for (FilePath src : paths) {
						String key = "";
						if (Utils.isNullOrEmpty(expVP)
								&& Utils.isNullOrEmpty(embeddedVP)) {
							key = src.getName();
						} else {
							String prefix = expVP;
							if (!Utils.isNullOrEmpty(embeddedVP)) {
								if (Utils.isNullOrEmpty(expVP)) {
									prefix = embeddedVP;
								} else {
									prefix = expVP + embeddedVP;
								}
							}
							key = prefix + src.getName();
						}
						long startTime = System.currentTimeMillis();
						InputStream inputStream = src.read();
						try {
							ObjectMetadata meta = new ObjectMetadata();
							meta.setContentLength(src.length());
							meta.setContentType(getContentType(src));
							listener.getLogger().println("File: " + src.getName() + ", Content Type: " + meta.getContentType());
							client.putObject(bucketName, key, inputStream, meta);
						} finally {
							try {
								inputStream.close();
							} catch (IOException e) {
							}
						}
						long endTime = System.currentTimeMillis();
						listener.getLogger().println("Uploaded object ["+ key + "] in " + getTime(endTime - startTime));
						listener.getLogger().println("版本下载地址:"+"http://"+bucketName+"."+location+aliyunEndPointSuffix+"/"+key);
						filesUploaded++;
					}
				}else {
					listener.getLogger().println(expFP+"下未找到Artifacts，请确认\"要上传的Artifacts\"中路径配置正确或部署包已正常生成，如pom.xml里assembly插件及配置正确。");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new AliyunOSSException(e.getMessage(), e.getCause());
		}
		return filesUploaded;
	}

	public static String getTime(long timeInMills) {
		return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S") + " (HH:mm:ss.S)";
	}


	// https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
	// support the common web file types for now
	private static final String[] COMMON_CONTENT_TYPES = {
			".js", 		"application/js",
			".json", 	"application/json",
			".svg", 	"image/svg+xml",
			".woff", 	"application/x-font-woff",
			".woff2", 	"application/x-font-woff",
			".ttf", 	"application/x-font-ttf"
	};

	// http://www.rgagnon.com/javadetails/java-0487.html
	// we don't use the more robust solutions (checking magic headers) here, because the file stream might be
	// loaded remotely, so that would be time consuming in checking, even hangs the Jenkins build in my test.
	private static String getContentType(FilePath filePath) {
		FileNameMap fileNameMap = URLConnection.getFileNameMap();
		String fileName = filePath.getName();
		String type = fileNameMap.getContentTypeFor(fileName);

		if (type == null) {
			for (int i = 0; i < COMMON_CONTENT_TYPES.length; i += 2) {
				String extension = COMMON_CONTENT_TYPES[i];
				int beginIndex = Math.max(0, fileName.length() - extension.length());
				if (fileName.substring(beginIndex).equals(extension)) {
					return COMMON_CONTENT_TYPES[i + 1];
				}
			}
			type = "application/octet-stream";
		}
		return type;
	}

}
