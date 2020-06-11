#include <D4FileParser.h>
#include <d4.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
extern "C" {
	JNIEXPORT jlong JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1open
	  (JNIEnv * env, jclass cls, jstring path_obj) {
		const char* path = env->GetStringUTFChars(path_obj, 0);
		printf("Opening D4 file: %s\n", path);
		return (jlong) d4_open(path, "r");
	}

	JNIEXPORT void JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1close
	  (JNIEnv * env, jclass cls, jlong addr) {
		  d4_file_t* handle = (d4_file_t*)addr;
		  d4_close(handle);
	}

	struct bucket_info_t {
		const char* chrom;
		int left, right;
		uint32_t count;
		int32_t min, max;
		float sum;
	};

	struct stat_result_t {
		size_t size;
		bucket_info_t* data;
	};
	struct bucket_ctx_t {
		bucket_info_t* info;
		int left, right;
		int32_t min, max;
		float sum;
	};

	struct task_ctx_t {
		unsigned size;
		bucket_ctx_t* buckets;
	};

	static void* task_init(d4_task_part_t* handle, void* extra_data) {
		bucket_info_t* buckets = (bucket_info_t*)extra_data;
		bucket_ctx_t* buckets_buf = (bucket_ctx_t*)malloc(32 * sizeof(bucket_ctx_t));
		memset(buckets_buf, 0, sizeof(bucket_ctx_t) * 32);

		unsigned capacity = 32;
		unsigned count = 0;

		char name[32];
		uint32_t l, r;

		d4_task_chrom(handle, name, 32);
		d4_task_range(handle, &l, &r);

		for(int i = 0; buckets[i].chrom != NULL; i ++) {
			
			if(strcmp(name, buckets[0].chrom)) {
				continue;
			}

			if(buckets[i].right < l) {
				continue;
			} else if(r < buckets[i].left) {
				break;
			}

			if(count >= capacity) {
				buckets_buf = (bucket_ctx_t*)realloc(buckets_buf, capacity * 2 * sizeof(bucket_ctx_t));
				capacity *= 2;
			}

			uint32_t il = l, ir = r;

			if(il < buckets[i].left) il = buckets[i].left;
			if(buckets[i].right < ir) ir = buckets[i].right;

			buckets_buf[count].info = buckets + i;
			buckets_buf[count].left = il;
			buckets_buf[count].right = ir;
			buckets_buf[count].sum = 0;

			buckets_buf[count].min = 0x7fffffff;
			buckets_buf[count].max = 0x80000000;

			count += 1;
				
		}

		task_ctx_t* ctx = (task_ctx_t*)malloc(sizeof(task_ctx_t));
		ctx->size = count;
		ctx->buckets = buckets_buf;

		return ctx;
	}

	static int task_proc(d4_task_part_t* handle, void* task_context, void* extra_data) {
		uint32_t l, r;
		d4_task_range(handle, &l, &r);
		uint32_t pos;
		task_ctx_t* ctx = (task_ctx_t*)task_context;
		if(NULL == task_context || ctx->size == 0) {
			return 0;
		}
		int current = 0;
		for(pos = l; pos < r; ) {
			int32_t buffer[10000];
			int count = d4_task_read_values(handle, pos, buffer, sizeof(buffer) / sizeof(int32_t));
			for(int i = 0; i < count; i ++) {
				int value_pos = pos + i;
				
				if(ctx->buckets[current].right <= value_pos) {
					current += 1;
				}
				if(current >= ctx->size) goto EXIT;


				if(ctx->buckets[current].left <= value_pos) 
				{
					ctx->buckets[current].sum += buffer[i];

					if(ctx->buckets[current].min > buffer[i])
						ctx->buckets[current].min = buffer[i];
					
					if(ctx->buckets[current].max < buffer[i])
						ctx->buckets[current].max = buffer[i];
				}

			}
			pos += count;
		}
	EXIT:
		return 0;
	}

	static int task_clean(d4_task_part_result_t* result, size_t count, void* extra) {
		for(size_t i = 0; i < count; i ++) {
			task_ctx_t* ctx = (task_ctx_t*) result[i].task_context;
			if(NULL == ctx) continue;
			for(size_t j = 0; j < ctx->size; j ++) {
				ctx->buckets[j].info->sum += ctx->buckets[j].sum;
				ctx->buckets[j].info->count += ctx->buckets[j].right - ctx->buckets[j].left;

				if(ctx->buckets[j].info->min > ctx->buckets[j].min)
					ctx->buckets[j].info->min = ctx->buckets[j].min;
				
				if(ctx->buckets[j].info->max < ctx->buckets[j].max)
					ctx->buckets[j].info->max = ctx->buckets[j].max;
			}
			free(ctx->buckets);
			free(ctx);
		}
		return 0;
	}

	JNIEXPORT jlong JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1run_1stat
	  (JNIEnv * env, jclass cls, jlong handle, jint opcode, jstring chr, jint left, jint right, jint count) {
		  d4_file_t* file = (d4_file_t*)handle;

		  right += 1;

		  if(count > right - left) {
			  count = right - left;
		  }

		  bucket_info_t* buckets = (bucket_info_t*)calloc(sizeof(bucket_info_t), count + 1);

		  int unit_size = (right - left) / count;
		  int rem = (right - left) % count;
		  int used = 0;

		  const char* chrom = env->GetStringUTFChars(chr, 0);

		  for(int i = 0; i < count ; i ++) {
			  buckets[i].chrom = chrom;
			  buckets[i].left = left + i * unit_size + used;

			  if(used < rem) used += 1;

			  buckets[i].right = buckets[i].left + unit_size;
			  buckets[i].count = 0;
			  buckets[i].sum = 0;
			  buckets[i].min = 0x7fffffff;
			  buckets[i].max = 0x80000000;
		  }
		  d4_task_desc_t task = {
				.mode = D4_TASK_READ,
				.part_size_limit = 10000000,
				.num_cpus = 0,
				.part_context_create_cb = task_init,
				.part_process_cb = task_proc,
				.part_finalize_cb = task_clean,
				.extra_data = buckets,
		};

		d4_file_run_task(file, &task);

		stat_result_t* result = (stat_result_t*)malloc(sizeof(stat_result_t));
		result->data = buckets;
		result->size = count;

		return (jlong)result;
	}

	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1free
	  (JNIEnv * env, jclass cls, jlong handle) {
		stat_result_t* result = (stat_result_t*)handle;
		free(result->data);
		free(result);
		return 0;
	}

	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1size
	  (JNIEnv * env, jclass cls, jlong handle) {
		stat_result_t* result = (stat_result_t*)handle;
		return result->size;
	}

	JNIEXPORT jfloat JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1get_1value
	  (JNIEnv * env, jclass cls, jlong handle, jint idx) {
		stat_result_t* result = (stat_result_t*)handle;
		if(result->size > idx) return result->data[idx].sum / result->data[idx].count;
		return 0;
	}

	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1get_1max
	  (JNIEnv * env, jclass cls, jlong handle, jint idx) {
		stat_result_t* result = (stat_result_t*)handle;
		if(result->size > idx) return result->data[idx].max;
		return 0;
	}
	
	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1get_1min
	  (JNIEnv * env, jclass cls, jlong handle, jint idx) {
		stat_result_t* result = (stat_result_t*)handle;
		if(result->size > idx) return result->data[idx].min;
		return 0;
	}

	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1get_1begin
	  (JNIEnv * env, jclass cls, jlong handle, jint idx) {
		stat_result_t* result = (stat_result_t*)handle;
		if(result->size > idx) return result->data[idx].left;
		return 0;
	}

	JNIEXPORT jint JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1stat_1get_1end
	  (JNIEnv * env, jclass cls, jlong handle, jint idx) {
		stat_result_t* result = (stat_result_t*)handle;
		if(result->size > idx) return result->data[idx].right;
		return 0;
	}
	JNIEXPORT jobjectArray JNICALL Java_org_broad_igv_d4_D4FileParser_d4_1chrom_1name
	  (JNIEnv * env, jclass cls, jlong handle) {
		  d4_file_t* file = (d4_file_t*)handle;
		  d4_file_metadata_t mtdt = {};
		  d4_file_load_metadata(file, &mtdt);
		  jobjectArray ret = (jobjectArray)env->NewObjectArray(mtdt.chrom_count, env->FindClass("java/lang/String"), env->NewStringUTF(""));
		  for(int i = 0; i < mtdt.chrom_count; i ++) {
			  const char* chrom = mtdt.chrom_name[i];
			  env->SetObjectArrayElement(ret, i, env->NewStringUTF(chrom));
		  }
		  d4_file_metadata_clear(&mtdt);
		  return ret;
	}
}
#ifdef TEST_MAIN
int main() {
	JNIEnv env;
	jlong handle = Java_org_broad_igv_d4_D4FileParser_d4_1open(&env, NULL, "../../data/hg002.d4");
	for(int j = 0; j < 1; j ++){




	stat_result_t* result = (stat_result_t*)Java_org_broad_igv_d4_D4FileParser_d4_1run_1stat(&env, NULL, handle, 0, "1", 0, 25000, 32);
	for(int i = 0; i < 32; i ++)
		printf("%lf %d %d %d\n", result->data[i].sum, result->data[i].count, result->data[i].min, result->data[i].max);
	

	
	}
	Java_org_broad_igv_d4_D4FileParser_d4_1close(&env, NULL, handle);

	return 0;
}
#endif
