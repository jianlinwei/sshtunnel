## 快速设置指南 ##

使用本软件前请确认满足以下两个条件中的任意一个：1. 有自己的 VPS 或者独立服务器；2. 有 Puff 或者类似服务商的 SSH 帐号。

Puff 用户请直接下载专用版 http://sshtunnel.googlecode.com/files/PUFF.apk

### 服务器设置 ###

满足第二项条件的用户可跳过此部分。

1. 在 VPS 或者服务器上安装任意 Linux 操作系统。

2. 安装 Squid 代理服务器。Ubuntu / Debian : **sudo apt-get install squid**, Redhat / CentOS / Fedora : **sudo yum install squid**

3. 以默认配置启动 Squid 即可。

### 手机设置 ###

1. 下载 SSHTunnel 最新版

2. 启动后填入以下参数：

> Host 服务器地址

> Port 一般为 22，Puff 用户还可填 443

> User, Password 你的 SSH 用户名与密码

> Local Port 任意大于 1024 的数字即可，如 1984

> PUFF 用户和自建服务用户，Remote Port 填入 3128

> 其他用户请启用“**使用SOCKS代理**”选项，Remote Port 留空即可

3. 点击 Connect 按钮

4. 非 Root 用户请手动设置手机的代理，如 127.0.0.1:1984

更多说明见这里：http://madeye.me/2011/02/10/ssh-tunnel-on-the-android-application-puff-android-edition/

一个图文教程见这里：
http://goo.gl/FfrXt