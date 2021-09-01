use jni::{
    objects::{JClass, JString},
    sys, JNIEnv,
};

#[allow(non_snake_case)]
#[no_mangle]
pub unsafe extern "C" fn Java_com_leaf_example_aleaf_SimpleVpnService_runLeaf(
    env: JNIEnv,
    _: JClass,
    config_path: JString,
) {
    let config_path = env
        .get_string(config_path)
        .unwrap()
        .to_str()
        .unwrap()
        .to_owned();
    let opts = leaf::StartOptions {
        config: leaf::Config::File(config_path),
        runtime_opt: leaf::RuntimeOption::MultiThreadAuto(1 * 1024 * 1024),
    };
    leaf::start(0, opts).unwrap();
}

#[allow(non_snake_case)]
#[no_mangle]
pub unsafe extern "C" fn Java_com_leaf_example_aleaf_SimpleVpnService_stopLeaf(
    _: JNIEnv,
    _: JClass,
) -> sys::jboolean {
    if leaf::shutdown(0) {
        sys::JNI_TRUE
    } else {
        sys::JNI_FALSE
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub unsafe extern "C" fn Java_com_leaf_example_aleaf_SimpleVpnService_isLeafRunning(
    _: JNIEnv,
    _: JClass,
) -> sys::jboolean {
    if leaf::is_running(0) {
        sys::JNI_TRUE
    } else {
        sys::JNI_FALSE
    }
}
