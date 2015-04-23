FIT2CLOUD Aliyun-OSS-Plugin for Jenkins
====================
建立统一的artifacts仓库是后续的持续部署的前提。目前，建立artifacts仓库大致有如下三种选择：

1. FTP服务器：很多用户仍然在用这种方式存储Artifact
2. 专业的Artifacts存储仓库：比如Nexus, Artifactory等。
3. 对象存储服务：比如阿里云OSS，AWS S3等。如果用户的应用系统全部部署在阿里云中，那么使用阿里云OSS来建立artifacts仓库的好处是，a)可靠性、高可用性 b) 上传、下载速度快。

Jenkins是当前最常用的CI服务器，FIT2CLOUD Aliyun-OSS-Plugin for Jenkins的功能是：将构建后的artifact上传到OSS的指定位置上去。
 	
一、安装说明
-------------------------

插件下载地址：http://repository-proxy.fit2cloud.com:8080/content/repositories/releases/org/jenkins-ci/plugins/aliyun-oss/0.5/aliyun-oss-0.5.hpi
在Jenkins中安装插件, 请到 Manage Jenkins | Advanced | Upload，上传插件(.hpi文件)
安装完毕后请重新启动Jenkins

二、配置说明
-------------------------

在使用插件之前，必须先在[Manage Jenkins | Configure System | 阿里云OSS账户设置]中配置阿里云帐号的Access Key和Secret Key.


三、Post-build actions: 上传Artifact到阿里云OSS
-------------------------

在Jenkins Job的Post-build actions，用户可以设上传Artifact到阿里云OSS。需要填写的信息是：

1. Bucket名称: artifact要存放的bucket
2. 要上传的artifacts: 文件之间用;隔开。支持通配符描述，比如 text/*.zip
3. Object前缀设置：可以设置object key的前缀，支持Jenkins环境变量比如: "${JOB_NAME}/${BUILD_ID}/${BUILD_NUMBER}/"

假设一个job的名称是test，用户的设置如下

1. bucketName: f2c
2. 要上传的artifacts: hello.txt;hello1.txt
3. Object前缀: ${JOB_NAME}/${BUILD_ID}/${BUILD_NUMBER}

那么上传后的文件url为: http://f2c.oss-cn-hangzhou.aliyuncs.com/test/2015-01-20_14-22-46/5/hello.txt


四、插件开发说明
-------------------------

1. git clone git@github.com:fit2cloud/aliyun-oss-plugin.git
2. mvn -Declipse.workspace=aliyun-oss-plugin eclipse:eclipse eclipse:add-maven-repo
3. import project to eclipse
4. mvn jdi:run 进行本地调试
5. mvn package 打包生成hpi文件

如果有问题，请联系zhimin@fit2cloud.com
