#include <stdint.h>
#define JNIEXPORT
#define JNICALL
typedef intptr_t jlong;
typedef int32_t jint;
typedef void* jclass;
typedef const char* jstring;
struct  JNIEnv  {
	const char* GetStringUTFChars(jstring str, int dumb) {
		return str;
	}
};

typedef float jfloat;
