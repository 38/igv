all: libd4-igv.so d4-igv-test
libd4-igv.so: D4FileParser.cpp D4FileParser.h
	$(CC) -o $@ \
		--shared  \
		-fPIC \
		-O3 \
		-I . -I /usr/lib/jvm/java-11-openjdk-amd64/include -I /usr/lib/jvm/java-11-openjdk-amd64/include/linux/ \
		$^ \
		-L. -ld4binding
d4-igv-test: D4FileParser.cpp 
	$(CC) -o $@ \
		-g \
		-I . -I dummy \
		-D TEST_MAIN \
		$^ \
		-L. -ld4binding

