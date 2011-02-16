#!/system/bin/sh

DIR=/data/data/org.sshtunnel

PATH=$DIR:$PATH

case $1 in
 start)

echo "
base {
 log_debug = off;
 log_info = off;
 log = stderr;
 daemon = on;
 redirector = iptables;
}
" >$DIR/redsocks.conf

   echo "
redsocks {
 local_ip = 127.0.0.1;
 local_port = 8123;
 ip = 127.0.0.1;
 port = $2;
 type = http-relay;
} 
redsocks {
 local_ip = 127.0.0.1;
 local_port = 8124;
 ip = 127.0.0.1;
 port = $2;
 type = http-connect;
} 
" >>$DIR/redsocks.conf
  echo "nameserver 8.8.8.8" > /etc/resolv.conf
  $DIR/redsocks -p $DIR/redsocks.pid -c $DIR/redsocks.conf
  ;;
stop)
  kill `cat $DIR/redsocks.pid`
  
  rm $DIR/redsocks.pid
  
  rm $DIR/redsocks.conf
  ;;
esac
