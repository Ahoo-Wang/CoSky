import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {useState} from "react";
import {Plus} from "lucide-react";
import {serviceApiClient} from "../../services/clients.ts";
import {toast} from "sonner";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";

export interface AddServiceFormProps {
    namespace: string;
    onSuccess: () => void;
}

export function AddServiceForm({namespace, onSuccess}: AddServiceFormProps) {
    const [serviceId, setServiceId] = useState('');
    const {loading, execute} = useExecutePromise({
        onSuccess: () => {
            toast.success('Add service success!');
            onSuccess();
            setServiceId('');
        },
        onError: () => {
            toast.error('Add service failed!');
        }
    });
    const handleFinish = async () => {
        await execute(() => {
            return serviceApiClient.setService(namespace, serviceId);
        })
    };

    return (
        <form className="flex flex-wrap items-center gap-2" onSubmit={(event) => {event.preventDefault(); handleFinish();}}>
            <Input value={serviceId} onChange={event => setServiceId(event.target.value)} placeholder="Enter service ID" required className="w-52"/>
            <Button type="submit" loading={loading}><Plus/>Add Service</Button>
        </form>
    )
}
