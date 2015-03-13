## 问题 ##

受限于手机性能，“安全隧道”不推荐使用本地 SOCKS 代理，建议在服务器上部署相应的代理服务。而对于大多数虚拟主机用户，因为得不到安装软件的权限，故无法正常安装配置 squid 服务。

## 解决方案 ##

绝大多数虚拟主机用户，在其目录下都具有执行的权限 （CGI / PHP），于是可以通过如下办法建立自己的代理服务：

1. 登陆远程服务器;

2. 下载 tinyproxy static 编译版;

3. 解压到用户目录下;

4. 执行其中的脚本 "proxy.sh start"

5. 在“安全隧道”的设置中，将远程端口改为 3128 （取决于你在 tinyproxy.conf 中的设置，默认是 3128）

## 命令行 ##

使用 putty, ssh, iSSH 连接到服务器，运行如下命令行即可：

```
cd ~
wget http://sshtunnel.googlecode.com/files/tinyproxy-static.tar.gz
tar xvf tinyproxy-static.tar.gz
cd tp_32
setsid ./tinyproxy -c tinyproxy.conf &
```

## 测试版 ##

为了方便非专业用户完成以上步骤，最新测试版添加了 “一键远程启动服务”功能，在程序主界面按 menu 键，并选择相应功能即可。