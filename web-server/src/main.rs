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
    let thread_tcp_chat = tokio_runtime.spawn(async move {
        tcp_chat().await;
    });

    thread_http_server.await.unwrap();
    thread_tcp_chat.await.unwrap();
}

async fn hello(param: HashMap<String, String>) -> Result<impl warp::Reply, warp::Rejection> {
    Ok(format!("{:#?}", param))
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

static CLOSED_CHAT : AtomicBool = AtomicBool::new(false);

async fn tcp_chat() {
    println!("started tcp_chat");

    let listener = TcpListener::bind("localhost:8080").await.unwrap();

    // tx -> transmitter
    // rx -> receiver
    let (tx, _rx) = broadcast::channel(10);

    loop {
        if CLOSED_CHAT.load(Ordering::SeqCst) == true {
            println!("finished tcp_chat");
            break;
        }

        let tx = tx.clone();
        let mut rx = tx.subscribe();

        // Open the socket.
        let (mut socket, addr) = listener.accept().await.unwrap();

        tokio::spawn(async move {
            let (reader, mut writer) = socket.split();

            // Wrap the buffer so it's possible to do more things like read entire lines.
            let mut reader = BufReader::new(reader);
            let mut line = String::new();
            let closing_message = "Closing the TCP chat.\n";

            loop {
                tokio::select! {
                    // Declare 2 concurrent branches which can share state.
                    // The select method allows to automatically assure that the concurrent branches are not executed in parallel by different threads.

                    // Send message(s).
                    result = reader.read_line(&mut line) => {
                        if result.unwrap() == 0 {
                            break;
                        }

                        tx.send((line.clone(), addr)).unwrap();
                        if line.trim() == "close" {
                            tx.send((closing_message.to_owned(), addr)).unwrap();
                            println!("Sent command to close chat.");
                            break;
                        }
                        line.clear();
                    }

                    // Receive message(s).
                    result = rx.recv() => {
                        let (msg, other_addr) = result.unwrap();

                        // Don't send the message to who wrote it.
                        if addr != other_addr {
                            writer.write_all(msg.as_bytes()).await.unwrap();

                            if msg.trim() == closing_message.trim() {
                                println!("{}", closing_message);
                                CLOSED_CHAT.store(true, Ordering::SeqCst);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }
    
}
