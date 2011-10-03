#!/bin/sh

#i=1

sed -r 's/\s*\[java\] //' | while read line; do

    if [ "x$line" = "xcluster:" ]; then
        read cluster
        
        for sample in $cluster; do
            echo "$sample"
        #done | sed -r 's/[0-9]+\(([0-9]+),([0-9]+)\)/'$i' \1 \2/g'
        done | sed -r 's/[0-9]+\(([0-9]+),([0-9]+)\)/\1 \2/g'
        
        echo
        echo
        #i=$((i+1))
    fi

done




