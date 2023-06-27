use uuid::Uuid;
use yew::prelude::*;

pub fn document(uuid: Uuid) -> Html {
    html! {
        <div class="grid-cols-3 lg:grid-cols-7 grid-rows-[2%_auto_auto] lg:grid-rows-[2%_98%] grid h-screen w-screen">
            <div class="hidden lg:block col-span-1 row-span-2 bg-red-700"></div>
            <div class="col-span-3 lg:col-span-6 bg-purple-500"></div>
            <div class="col-span-3 bg-green-700"></div>
            <div class="col-span-3 bg-blue-700"></div>
        </div>
    }
}
