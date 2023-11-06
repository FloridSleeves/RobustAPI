import fire
import os
import json
import tqdm
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer, GenerationConfig

from utils import generatePrompt, generateShot

def main(
    ckpt_path: str,
    tokenizer_path: str,
    result_dir: str,
    shot_number: int, # 0 for zero-shot, 1 for one-shot, -1 for irrelevant one-shot
    temperature: float = 0.2,
    top_p: float = 0.9,
    max_seq_len: int = 6000,
    max_gen_len: int = 1000,
    max_batch_size: int = 2,
    gpu: int = 0,
    question: str = "./dataset/question.jsonl",
):
    os.makedirs(result_dir, exist_ok=True)
        
    print("Loading model and tokenizer...")
    device = torch.device("cuda:%d" % gpu)
    tokenizer = AutoTokenizer.from_pretrained(tokenizer_path, trust_remote_code=True)
    
    if 'llama' in tokenizer_path.lower():
        tokenizer.pad_token = "[PAD]"
        tokenizer.padding_side = "left"
        
    model = AutoModelForCausalLM.from_pretrained(ckpt_path, trust_remote_code=True).to(device)
    
    print("Generating prompt samples...")
    samples = []
    with open(question, "r") as f:
        lines = f.readlines()
        prompts = [line.strip() for line in lines]
        for i, p in enumerate(prompts):
            check_path = os.path.join(result_dir, str(i) + ".json")
            if os.path.isfile(check_path):
                continue
            p = json.loads(p)
            shots = []
            try:
                shots = generateShot(p['api'], number = shot_number)
            except:
                print("ERROR: No shots for api", p['api'])
                continue
            prompt = generatePrompt(p['api'], p['question'], shots)
            samples.append((prompt, i, p['api']))
    print("Total samples:", len(samples))
    
    print("Generating responses...")
    for batch_idx in tqdm.tqdm(range(0, len(samples), max_batch_size)):
        batch = samples[batch_idx:batch_idx + max_batch_size]
        prompts = [b[0] for b in batch]
        indices = [b[1] for b in batch]
        apis = [b[2] for b in batch]
        inputs = tokenizer(prompts, return_tensors="pt", padding=True).to(model.device)
        
        generation_config = GenerationConfig(
            do_sample=True,
            temperature=temperature,
            top_p=top_p,
            max_length=max_seq_len,
            max_new_tokens=max_gen_len,
        )
        if 'deepseek' in ckpt_path.lower():
            generation_config.pad_token_id = tokenizer.eos_token_id

        try:
            outputs = model.generate(**inputs, generation_config=generation_config)
        except Exception as e:
            print("ERROR:", e, flush=True)
            continue
        
        for prompt, qid, api, output in zip(prompts, indices, apis, outputs):
            generation_dict = {'api': api, 
                               'prompt': prompt, 
                               'response': tokenizer.decode(output, skip_special_tokens=True)}
            fout = open(os.path.join(result_dir, str(qid) + ".json"), 'w')
            fout.write(json.dumps(generation_dict))
            fout.close()
    print("Done!")

if __name__ == "__main__":
    fire.Fire(main)
