
filename = "clusterdata.txt"

#set logscale x
#set logscale y

set xlabel "In Packet size"
set ylabel "In Packets"

set terminal png
set output "clusters.png"


#plot filename using 1:2 with lines notitle

#plot filename index 3 using 1:2 with points ps 2 notitle

plot filename index 0 using 1:2 with points ps 2 notitle, \
     filename index 1 using 1:2 with points ps 2 notitle, \
     filename index 2 using 1:2 with points ps 2 notitle #, \
#     filename index 3 using 1:2 with points ps 2 notitle
