#include <stdint.h>
#define JNIEXPORT
#define JNICALL
typedef intptr_t jlong;
typedef int32_t jint;
typedef void* jclass;
typedef void* jobjectArray;
typedef const char* jstring;
struct  JNIEnv  {
	const char* GetStringUTFChars(jstring str, int dumb) {
		return str;
	}

	jobjectArray NewObjectArray(int size, void* c, void* value) {
		return nullptr;
	}

	void* FindClass(const char* name) {
		return nullptr; 
	}

	void* NewStringUTF(const char* str) {
		return nullptr;
	}

	void SetObjectArrayElement(void* obj, int i, void* val) {
	}
};

typedef float jfloat;
