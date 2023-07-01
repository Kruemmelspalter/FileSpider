use uuid::Uuid;
use yew::prelude::*;

#[derive(Properties, PartialEq)]
pub struct DocumentViewerProps {
    pub id: Uuid,
}

#[function_component]
pub fn DocumentViewer(props: &DocumentViewerProps) -> Html {
    html! {
        <div class="grid-cols-3 landscape:grid-cols-7 grid-rows-[2%_auto_auto] landscape:grid-rows-[2%_98%] grid h-screen w-screen">
            <div class="hidden landscape:block col-span-1 row-span-2 bg-red-700"></div>
            <div class="col-span-3 landscape:col-span-6 bg-purple-500">
                {props.id}
            </div>
            <div class="col-span-3 bg-green-700"></div>
            <div class="col-span-3 bg-blue-700"></div>
        </div>
    }
}
