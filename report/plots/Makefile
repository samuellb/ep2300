
all: clusters.png

clusters.png: clusters.gnuplot clusterdata.txt
	gnuplot clusters.gnuplot

clusterdata.txt: clusterdata.sh monitor_output.txt
	./clusterdata.sh < monitor_output.txt > clusterdata.txt



.PHONY: all clean
clean:
	-$(RM) clusters.png clusterdata.txt



