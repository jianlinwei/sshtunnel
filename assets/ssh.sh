ps#!/system/bin/sh

DIR=/data/data/org.sshtunnel.beta

case $1 in
  dynamic)

$DIR/sshtunnel -i /data/data/org.sshtunnel.beta/.ssh/private_key -CNT -p $2 -D $3 -L 127.0.0.1:5353:8.8.8.8:53 $4@$5
  
  ;;
  local)

$DIR/sshtunnel -i /data/data/org.sshtunnel.beta/.ssh/private_key -CNT -p $2 -L 127.0.0.1:$3:$4:$5 -L 127.0.0.1:5353:8.8.8.8:53 $6@$7
  
  ;;
esac

exit