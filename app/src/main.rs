mod app;
mod document;

use app::App;

fn main() {
    yew::Renderer::<App>::new().render();
}
