for(i of document.getElementsByTagName("img")){
    console.log(i);
    const imageUrl = i.dataset.url;
    const fileNameParts = imageUrl.split('/');
    const fileName = fileNameParts[fileNameParts.length - 1];
    const prompt = i.dataset.prompt;
    i.addEventListener('click', () => {
        console.log('WEOWEO', imageUrl, prompt, fileName)
        const data = {
            prompt: prompt,
            selectedImage: fileName,
        };
        const fetchPromise = fetch("http://localhost:8666/select", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
            },
            body: JSON.stringify(data),
        });
        fetchPromise.then((e) => {
            setTimeout(() => {
                window.location.reload();
            }, 100)
        })
    });
}