use uuid::{uuid, Uuid};
use wasm_bindgen::prelude::*;
use yew::prelude::*;
use yew_router::prelude::*;

use crate::document::DocumentViewer;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = ["window", "__TAURI__", "tauri"])]
    async fn invoke(cmd: &str, args: JsValue) -> JsValue;
}

#[derive(Clone, Routable, PartialEq)]
enum Route {
    #[at("/")]
    Home,
    #[at("/document/:id")]
    Document { id: Uuid },
    #[not_found]
    #[at("/404")]
    NotFound,
}

#[function_component(App)]
pub fn app() -> Html {
    html! {
        <BrowserRouter>
            <Switch<Route> render={|route| match route {
                Route::Home => home(),
                Route::Document { id } => html! { <DocumentViewer id={id}/>},
                Route::NotFound => html! {<h1>{ "Not Found!" }</h1>},
            }} />
        </BrowserRouter>
    }
}

fn home() -> Html {
    html! {
    <>
        {"home"}
        <Link<Route> to={Route::Document { id: uuid!("73426d5b-1ead-4670-b13e-aa3c5358fc92")}}>{ "doucmen" }</Link<Route>>
    </>
    }
}
