use jni::{
    objects::{JClass, JString},
    JNIEnv,
};

#[allow(non_snake_case)]
#[no_mangle]
pub unsafe extern "C" fn Java_com_leaf_example_aleaf_SimpleVpnService_runLeaf(
    env: JNIEnv,
    _: JClass,
    config_path: JString,
    protect_path: JString,
) {
    let config_path = env
        .get_string(config_path)
        .unwrap()
        .to_str()
        .unwrap()
        .to_owned();
    let protect_path = env
        .get_string(protect_path)
        .unwrap()
        .to_str()
        .unwrap()
        .to_owned();
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap();
    rt.block_on(leaf::proxy::set_socket_protect_path(
        protect_path.to_string(),
    ));
    let config = leaf::config::from_file(&config_path).unwrap();
    let runners = leaf::util::prepare(config).unwrap();
    rt.block_on(futures::future::join_all(runners));
}
