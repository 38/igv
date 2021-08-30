UNAME_S := $(shell uname -s || echo Darwin)
ifeq ($(UNAME_S),Linux)
	D4BINDING = libd4binding.so
	JNILIB = libd4-igv.so
endif
ifeq ($(UNAME_S),Darwin)
	D4BINDING = libd4binding.dylib
	JNILIB = libd4-igv.dylib
endif

all: $(JNILIB) d4-igv-test
$(D4BINDING):
	cd d4-format && \
		cargo build --package=d4binding --release && \
		ln target/release/$@ ../
$(JNILIB): D4FileParser.cpp D4FileParser.h $(D4BINDING)
	$(CC) -o $@ \
		--shared  \
		-fPIC \
		-O3 \
		-I . -I /usr/lib/jvm/java-11-openjdk-amd64/include -I /usr/lib/jvm/java-11-openjdk-amd64/include/linux/ \
		$< \
		-L. -ld4binding
d4-igv-test: D4FileParser.cpp 
	$(CC) -o $@ \
		-g -O3 \
		-I . -I dummy \
		-D TEST_MAIN \
		$^ \
		-L. -ld4binding

