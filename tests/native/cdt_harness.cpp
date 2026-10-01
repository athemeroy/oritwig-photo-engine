// Test-only JNI buffer harness; exercises the actual extracted native file.
#include <jni.h>
#include <cstdint>
#include <cstdio>
#include <vector>
#include <stdexcept>
#include <cstring>
struct Buffer { void* data; jlong size; };
static bool threw;
static void* address(JNIEnv*, jobject b) { return b ? reinterpret_cast<Buffer*>(b)->data : nullptr; }
static jlong capacity(JNIEnv*, jobject b) { return b ? reinterpret_cast<Buffer*>(b)->size : -1; }
static jclass find(JNIEnv*, const char*) { return reinterpret_cast<jclass>(uintptr_t(1)); }
static jint fail(JNIEnv*, jclass, const char*) { threw=true;return 0; }
extern "C" void Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate(JNIEnv*,jclass,jobject,jint,jint,jobject,jobject);
static void verify(bool b,const char* text) { if(!b)throw std::runtime_error(text); }
int main() {
 JNINativeInterface_ f{}; f.GetDirectBufferAddress=address;f.GetDirectBufferCapacity=capacity;f.FindClass=find;f.ThrowNew=fail;JNIEnv env{&f};
 std::vector<uint32_t> work(16*2*257);std::vector<uint8_t> out(16*256*4+32,0x5a);
 Buffer w{work.data(),jlong(work.size()*4)},o{out.data()+16,16*256*4};int fixtures=0;
 for(int width=4;width<=80;width++)for(int height=4;height<=65;height+=3){
  std::vector<uint8_t> input(width*height*4);for(size_t i=0;i<input.size();i++)input[i]=uint8_t((i*37+19)%256);
  Buffer in{input.data(),jlong(input.size())};threw=false;
  Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate(&env,nullptr,reinterpret_cast<jobject>(&in),width,height,reinterpret_cast<jobject>(&o),reinterpret_cast<jobject>(&w));
  verify(!threw,"Valid dimensions rejected");for(int i=0;i<16;i++){verify(out[i]==0x5a,"Prefix overwritten");verify(out[out.size()-i-1]==0x5a,"Suffix overwritten");}
  for(int i=0;i<16*256;i++)verify(out[16+i*4+3]==255,"Kernel did not generate complete output");fixtures++;
 }
 uint8_t tiny[16]{};Buffer in{tiny,16};threw=false;
 Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate(&env,nullptr,reinterpret_cast<jobject>(&in),4,4,reinterpret_cast<jobject>(&o),reinterpret_cast<jobject>(&w));verify(threw,"Short input not rejected");
 printf("PASS: %d native calcCDT fixtures, guards and short input; ASan/UBSan enabled\n",fixtures);
}
