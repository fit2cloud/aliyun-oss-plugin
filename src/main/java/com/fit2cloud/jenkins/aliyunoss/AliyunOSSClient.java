package com.fit2cloud.jenkins.aliyunoss;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.ObjectMetadata;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class AliyunOSSClient {
    private static final String FP_SEPARATOR = ";";

    static void validateAliyunAccount(
            final String aliyunAccessKey,
            final String aliyunSecretKey,
            final String endpoint) throws AliyunOSSException {
        try {
            OSSClient client = new OSSClient(endpoint, aliyunAccessKey, aliyunSecretKey);
            client.listBuckets();
        } catch (Exception e) {
            throw new AliyunOSSException("阿里云账号验证失败：" + e.getMessage());
        }
    }


    static void validateOSSBucket(String aliyunAccessKey,
                                  String aliyunSecretKey,
                                  String endpoint,
                                  String bucketName) throws AliyunOSSException {
        try {
            OSSClient client = new OSSClient(endpoint, aliyunAccessKey, aliyunSecretKey);
            client.getBucketLocation(bucketName);
        } catch (Exception e) {
            throw new AliyunOSSException("验证Bucket名称失败：" + e.getMessage());
        }
    }

    public static int upload(AbstractBuild<?, ?> build, BuildListener listener,
                             final String aliyunAccessKey,
                             final String aliyunSecretKey,
                             final String endpoint,
                             String bucketName,
                             String expFP,
                             String expVP,
                             List<HeaderConfig> headers) throws AliyunOSSException {

        OSSClient client = new OSSClient(endpoint, aliyunAccessKey, aliyunSecretKey);
        // Counter to track no. of files that are uploaded
        int filesUploaded = 0;
        try {
            FilePath workspacePath = build.getWorkspace();
            if (workspacePath == null) {
                listener.getLogger().println("工作空间中没有任何文件.");
                return filesUploaded;
            }
            StringTokenizer strTokens = new StringTokenizer(expFP, FP_SEPARATOR);
            FilePath[] paths = null;

            listener.getLogger().println("开始上传到阿里云OSS...");
            listener.getLogger().println("上传endpoint是：" + endpoint);
            listener.getLogger().println();

            while (strTokens.hasMoreElements()) {
                String fileName = strTokens.nextToken();
                String embeddedVP = null;
                if (fileName == null) {
                    continue;
                }
                {
                    int embVPSepIndex = fileName.indexOf("::");
                    if (embVPSepIndex != -1) {
                        if (fileName.length() > embVPSepIndex + 1) {
                            embeddedVP = fileName.substring(embVPSepIndex + 2);
                            if (Utils.isNullOrEmpty(embeddedVP)) {
                                embeddedVP = null;
                            }
                            if (embeddedVP != null && !embeddedVP.endsWith(Utils.FWD_SLASH)) {
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

                            Map<String, String> configHeaders = getConfigHeaders(headers, src);
                            configHeaders.forEach(meta::setHeader);

                            listener.getLogger().println("File: " + src.getName() + ", ContentType: " + meta.getContentType() + ", Config Header: " + configHeaders.toString());
                            client.putObject(bucketName, key, inputStream, meta);

                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        long endTime = System.currentTimeMillis();
                        listener.getLogger().println("OSS Key: " + key + " in: " + getTime(endTime - startTime));
                        listener.getLogger().println("URL: " + "http://" + bucketName + "." + endpoint.replace("http://", "") + "/" + key);
                        listener.getLogger().println();
                        filesUploaded++;
                    }
                } else {
                    listener.getLogger().println(expFP + "下未找到Artifacts，请确认\"要上传的Artifacts\"中路径配置正确或部署包已正常生成，如pom.xml里assembly插件及配置正确。");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new AliyunOSSException(e.getMessage(), e.getCause());
        }
        return filesUploaded;
    }

    private static String getTime(long timeInMills) {
        return DurationFormatUtils.formatDuration(timeInMills, "HH:mm:ss.S") + " (HH:mm:ss.S)";
    }


    /**
     * https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
     */
    private static final Map<String, String> COMMON_CONTENT_TYPES = new HashMap<String, String>();

    static {
        COMMON_CONTENT_TYPES.put("aac", "audio/aac");
        COMMON_CONTENT_TYPES.put("abw", "application/x-abiword");
        COMMON_CONTENT_TYPES.put("arc", "application/x-freearc");
        COMMON_CONTENT_TYPES.put("avi", "video/x-msvideo");
        COMMON_CONTENT_TYPES.put("azw", "application/vnd.amazon.ebook");
        COMMON_CONTENT_TYPES.put("bin", "application/octet-stream");
        COMMON_CONTENT_TYPES.put("bmp", "image/bmp");
        COMMON_CONTENT_TYPES.put("bz", "application/x-bzip");
        COMMON_CONTENT_TYPES.put("bz2", "application/x-bzip2");
        COMMON_CONTENT_TYPES.put("csh", "application/x-csh");
        COMMON_CONTENT_TYPES.put("css", "text/css");
        COMMON_CONTENT_TYPES.put("csv", "text/csv");
        COMMON_CONTENT_TYPES.put("doc", "application/msword");
        COMMON_CONTENT_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        COMMON_CONTENT_TYPES.put("eot", "application/vnd.ms-fontobject");
        COMMON_CONTENT_TYPES.put("epub", "application/epub+zip");
        COMMON_CONTENT_TYPES.put("gif", "image/gif");
        COMMON_CONTENT_TYPES.put("htm", "text/html");
        COMMON_CONTENT_TYPES.put("html", "text/html");
        COMMON_CONTENT_TYPES.put("ico", "image/vnd.microsoft.icon");
        COMMON_CONTENT_TYPES.put("ics", "text/calendar");
        COMMON_CONTENT_TYPES.put("jar", "application/java-archive");
        COMMON_CONTENT_TYPES.put("jpeg", "image/jpeg");
        COMMON_CONTENT_TYPES.put("jpg", "image/jpeg");
        COMMON_CONTENT_TYPES.put("js", "text/javascript");
        COMMON_CONTENT_TYPES.put("json", "application/json");
        COMMON_CONTENT_TYPES.put("jsonld", "application/ld+json");
        COMMON_CONTENT_TYPES.put("mid", "audio/midi audio/x-midi");
        COMMON_CONTENT_TYPES.put("midi", "audio/midi audio/x-midi");
        COMMON_CONTENT_TYPES.put("mjs", "text/javascript");
        COMMON_CONTENT_TYPES.put("mp3", "audio/mpeg");
        COMMON_CONTENT_TYPES.put("mpeg", "video/mpeg");
        COMMON_CONTENT_TYPES.put("mpkg", "application/vnd.apple.installer+xml");
        COMMON_CONTENT_TYPES.put("odp", "application/vnd.oasis.opendocument.presentation");
        COMMON_CONTENT_TYPES.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
        COMMON_CONTENT_TYPES.put("odt", "application/vnd.oasis.opendocument.text");
        COMMON_CONTENT_TYPES.put("oga", "audio/ogg");
        COMMON_CONTENT_TYPES.put("ogv", "video/ogg");
        COMMON_CONTENT_TYPES.put("ogx", "application/ogg");
        COMMON_CONTENT_TYPES.put("otf", "font/otf");
        COMMON_CONTENT_TYPES.put("png", "image/png");
        COMMON_CONTENT_TYPES.put("pdf", "application/pdf");
        COMMON_CONTENT_TYPES.put("ppt", "application/vnd.ms-powerpoint");
        COMMON_CONTENT_TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        COMMON_CONTENT_TYPES.put("rar", "application/x-rar-compressed");
        COMMON_CONTENT_TYPES.put("rtf", "application/rtf");
        COMMON_CONTENT_TYPES.put("sh", "application/x-sh");
        COMMON_CONTENT_TYPES.put("svg", "image/svg+xml");
        COMMON_CONTENT_TYPES.put("swf", "application/x-shockwave-flash");
        COMMON_CONTENT_TYPES.put("tar", "application/x-tar");
        COMMON_CONTENT_TYPES.put("tif", "image/tiff");
        COMMON_CONTENT_TYPES.put("tiff", "image/tiff");
        COMMON_CONTENT_TYPES.put("ttf", "font/ttf");
        COMMON_CONTENT_TYPES.put("txt", "text/plain");
        COMMON_CONTENT_TYPES.put("vsd", "application/vnd.visio");
        COMMON_CONTENT_TYPES.put("wav", "audio/wav");
        COMMON_CONTENT_TYPES.put("weba", "audio/webm");
        COMMON_CONTENT_TYPES.put("webm", "video/webm");
        COMMON_CONTENT_TYPES.put("webp", "image/webp");
        COMMON_CONTENT_TYPES.put("woff", "font/woff");
        COMMON_CONTENT_TYPES.put("woff2", "font/woff2");
        COMMON_CONTENT_TYPES.put("xhtml", "application/xhtml+xml");
        COMMON_CONTENT_TYPES.put("xls", "application/vnd.ms-excel");
        COMMON_CONTENT_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        COMMON_CONTENT_TYPES.put("xml", "text/xml");
        COMMON_CONTENT_TYPES.put("xul", "application/vnd.mozilla.xul+xml");
        COMMON_CONTENT_TYPES.put("zip", "application/zip");
        COMMON_CONTENT_TYPES.put("3gp", "video/3gpp");
        COMMON_CONTENT_TYPES.put("3g2", "video/3gpp2");
        COMMON_CONTENT_TYPES.put("7z", "application/x-7z-compressed");
    }


    private static String getContentType(FilePath filePath) {

        String extension = FilenameUtils.getExtension(filePath.getName());

        return COMMON_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static Map<String, String> getConfigHeaders(List<HeaderConfig> headers, FilePath filePath) {
        String fileName = filePath.getName();
        String extension = FilenameUtils.getExtension(fileName);

        return headers.stream().filter((headerConfig -> {
            if (StringUtils.isBlank(headerConfig.getPostfix()) ||
                    StringUtils.isBlank(headerConfig.getName()) ||
                    StringUtils.isBlank(headerConfig.getValue())) {
                return false;
            }
            String[] strings = headerConfig.getPostfix().split(",");
            for (String postfix : strings) {
                if (Objects.equals(postfix, extension)) {
                    return true;
                }
            }
            return false;
        })).collect(Collectors.toMap(HeaderConfig::getName, HeaderConfig::getValue));
    }

}
