use tokio::{
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader},
    net::TcpListener,
    sync::broadcast,
};

use warp::Filter;

use std::{collections::HashMap};

use std::{time::Duration};

use std::sync::atomic::{AtomicBool, Ordering};

// ----- Ownership rules -----
// 1. Each value in Rust has a variable that's called its owner.
// 2. There can only be one owner at a time.
// 3. When the owner goes out of scope, the value will be dropped.

// ----- The Rules of References.
// 1. At any given time, you can have either one mutable reference
// or any number of immutable references.
// 2. References must always be valid.

# [tokio::main]
async fn main() {
    println!("Hello, world!");

    let tokio_runtime = tokio::runtime::Builder::new_multi_thread()
        .thread_keep_alive(Duration::from_secs(60))
        .enable_all()
        .build()
        .unwrap();

    let thread_http_server = tokio_runtime.spawn(async move {
        http_server().await;
    });

    thread_http_server.await.unwrap();
}

async fn hello(param: HashMap<String, String>) -> Result<impl warp::Reply, warp::Rejection> {
  println!("Called hello");
  Ok(format!("Params: {:#?}", param))
}

async fn http_server() {
    println!("started http_server");
    // GET /hello/warp => 200 OK with body "Hello, warp!"
    // https://docs.rs/warp/latest/warp/macro.path.html
    // The path! macro automatically assumes the path should include an end() filter.
    // To build up a path filter prefix, such that the end() isn’t included, use the / .. syntax.
    let hello = warp::get()
        .and(warp::path("hello"))
        .and(warp::query::<HashMap<String, String>>())
        .and(warp::path::end())
        .and_then(hello);

    warp::serve(hello)
        .run(([127, 0, 0, 1], 3030))
        .await;

    println!("finished http_server");
}
