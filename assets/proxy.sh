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

  mount -o rw,remount -t yaffs2 \
  /dev/block/mtdblock3 \
  /system

  cp -f /etc/hosts $DIR/hosts.bak
  cp -f $DIR/hosts /etc
  $DIR/redsocks -p $DIR/redsocks.pid -c $DIR/redsocks.conf
  
  mount -o ro,remount -t yaffs2 \
  /dev/block/mtdblock3 \
  /system
  ;;
stop)
  kill -9 `cat $DIR/redsocks.pid`
  
  mount -o rw,remount -t yaffs2 \
  /dev/block/mtdblock3 \
  /system
  
  cp -f $DIR/hosts.bak /etc/hosts
  
  rm $DIR/hosts.bak
  
  rm $DIR/redsocks.pid
  
  rm $DIR/redsocks.conf
  
  mount -o ro,remount -t yaffs2 \
  /dev/block/mtdblock3 \
  /system
  ;;
esac
